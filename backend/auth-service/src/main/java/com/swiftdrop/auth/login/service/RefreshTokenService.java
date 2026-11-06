package com.swiftdrop.auth.login.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshToken;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.dto.response.LoginUserSummary;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.exception.UnauthenticatedException;
import com.swiftdrop.auth.login.dto.IssuedRefresh;
import com.swiftdrop.auth.login.dto.RotationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token rotation service per SRS v2.1 Section 3.6, FR-AUTH-007, FR-AUTH-008,
 * and Master Prompt Section 8.1.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long refreshTtlDays;
    private final long refreshAbsoluteTtlDays;
    private final int graceSeconds;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            JwtService jwtService,
            @Value("${refresh.ttl-days:${REFRESH_TTL_DAYS:7}}") long refreshTtlDays,
            @Value("${refresh.absolute-ttl-days:${REFRESH_ABSOLUTE_TTL_DAYS:30}}") long refreshAbsoluteTtlDays,
            @Value("${refresh.grace-seconds:${REFRESH_GRACE_SECONDS:10}}") int graceSeconds) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.jwtService = jwtService;
        this.refreshTtlDays = refreshTtlDays;
        this.refreshAbsoluteTtlDays = refreshAbsoluteTtlDays;
        this.graceSeconds = graceSeconds;
    }

    /**
     * Issues a brand new refresh token family for the user upon fresh login.
     */
    @Transactional
    public IssuedRefresh issue(User user) {
        Instant now = Instant.now();
        UUID familyId = UUID.randomUUID();
        Instant familyCreatedAt = now;

        Instant calculatedExpiry = now.plus(refreshTtlDays, ChronoUnit.DAYS);
        Instant absoluteCap = familyCreatedAt.plus(refreshAbsoluteTtlDays, ChronoUnit.DAYS);
        Instant expiresAt = calculatedExpiry.isBefore(absoluteCap) ? calculatedExpiry : absoluteCap;

        String rawToken = generateOpaqueToken();
        String tokenHash = sha256Hex(rawToken);

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setFamilyCreatedAt(familyCreatedAt);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(now);
        token.setIssuedAt(now);

        RefreshToken saved = refreshTokenRepository.save(token);
        log.info("Issued fresh refresh token family={} for user={}", familyId, user.getId());
        return new IssuedRefresh(rawToken, familyId, expiresAt, saved);
    }

    /**
     * Backward-compatible token issuance helper by userId.
     */
    @Transactional
    public String createRefreshToken(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthenticatedException("User not found"));
        return issue(user).rawToken();
    }

    /**
     * Rotates an existing refresh token with pessimistic write locking, replay detection,
     * and concurrent grace window handling per Master Prompt 8.1.
     */
    @Transactional(noRollbackFor = {UnauthenticatedException.class})
    public RotationResult rotate(String rawToken) {
        return rotate(rawToken, null, null);
    }

    @Transactional(noRollbackFor = {UnauthenticatedException.class})
    public RotationResult rotate(String rawToken, String userAgent, String ipAddress) {
        if (rawToken == null || rawToken.isBlank()) {
            log.warn("Refresh failed: missing refresh token");
            throw new UnauthenticatedException("Refresh token is missing");
        }

        String tokenHash = sha256Hex(rawToken);
        Instant now = Instant.now();

        // 1. Lock the row with PESSIMISTIC_WRITE
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHashWithLock(tokenHash);
        if (tokenOpt.isEmpty()) {
            log.warn("Refresh failed: unknown token hash");
            throw new UnauthenticatedException("Invalid refresh token");
        }

        RefreshToken token = tokenOpt.get();

        // 2. Check if token or family revoked
        if (token.getRevokedAt() != null || refreshTokenRepository.isFamilyRevoked(token.getFamilyId())) {
            log.warn("Refresh rejected: family {} is revoked", token.getFamilyId());
            throw new UnauthenticatedException("Refresh token family has been revoked");
        }

        // 3. Check expiration
        if (now.isAfter(token.getExpiresAt())) {
            log.warn("Refresh rejected: token expired at {}", token.getExpiresAt());
            throw new UnauthenticatedException("Refresh token has expired");
        }

        // 4. Absolute family cap
        Instant maxFamilyExpiry = token.getFamilyCreatedAt().plus(refreshAbsoluteTtlDays, ChronoUnit.DAYS);
        if (now.isAfter(maxFamilyExpiry)) {
            log.warn("Refresh rejected: family {} exceeded absolute cap of {} days", token.getFamilyId(), refreshAbsoluteTtlDays);
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new UnauthenticatedException("Token family lifetime expired");
        }

        // 5. Replay detection / Concurrent grace window
        if (token.getUsedAt() != null) {
            long secondsSinceUsed = Duration.between(token.getUsedAt(), now).getSeconds();
            if (secondsSinceUsed <= graceSeconds) {
                log.info("Refresh concurrent-tab grace hit ({}s <= {}s) for family {}", secondsSinceUsed, graceSeconds, token.getFamilyId());
                User user = validateActiveUser(token.getUserId(), token.getFamilyId(), now);
                Merchant merchant = resolveAndValidateMerchant(user, token.getFamilyId(), now);

                String accessToken = jwtService.issueUserToken(user, merchant);
                LoginResponse response = buildLoginResponse(accessToken, user, merchant);
                return new RotationResult(response, null, true);
            } else {
                log.warn("SECURITY ALERT: Refresh token replay detected! Token used {}s ago (> {}s). Revoking family {}",
                        secondsSinceUsed, graceSeconds, token.getFamilyId());
                refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
                throw new UnauthenticatedException("Refresh token replay detected. Session terminated.");
            }
        }

        // 6. Normal rotation: mark current token used
        token.setUsedAt(now);
        refreshTokenRepository.save(token);

        // 7. Verify user and merchant status
        User user = validateActiveUser(token.getUserId(), token.getFamilyId(), now);
        Merchant merchant = resolveAndValidateMerchant(user, token.getFamilyId(), now);

        // 8. Generate new 256-bit cryptographically secure token
        String newRawRefreshToken = generateOpaqueToken();
        String newTokenHash = sha256Hex(newRawRefreshToken);

        Instant calculatedExpiry = now.plus(refreshTtlDays, ChronoUnit.DAYS);
        Instant expiresAt = calculatedExpiry.isBefore(maxFamilyExpiry) ? calculatedExpiry : maxFamilyExpiry;

        RefreshToken newToken = new RefreshToken();
        newToken.setUserId(user.getId());
        newToken.setFamilyId(token.getFamilyId());
        newToken.setFamilyCreatedAt(token.getFamilyCreatedAt());
        newToken.setTokenHash(newTokenHash);
        newToken.setExpiresAt(expiresAt);
        newToken.setCreatedAt(now);
        newToken.setIssuedAt(now);
        if (userAgent != null) newToken.setUserAgent(userAgent);
        if (ipAddress != null) newToken.setIpAddress(ipAddress);
        refreshTokenRepository.save(newToken);

        // 9. Issue new access token
        String accessToken = jwtService.issueUserToken(user, merchant);
        LoginResponse response = buildLoginResponse(accessToken, user, merchant);

        log.info("Refresh token rotated successfully for user={}, family={}", user.getId(), token.getFamilyId());
        return new RotationResult(response, newRawRefreshToken, false);
    }

    /**
     * Revokes the entire token family given the presented raw token (FR-AUTH-008).
     */
    @Transactional
    public void revokeFamily(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = sha256Hex(rawToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
            log.info("Revoked refresh familyId={}", token.getFamilyId());
        }
    }

    /**
     * Revokes all active refresh tokens for the given user.
     */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        if (userId == null) {
            return;
        }
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("Revoked all refresh tokens for userId={}", userId);
    }

    private User validateActiveUser(UUID userId, UUID familyId, Instant now) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    refreshTokenRepository.revokeFamily(familyId, now);
                    return new UnauthenticatedException("User account not found");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Refresh rejected: user {} has status {}", user.getId(), user.getStatus());
            refreshTokenRepository.revokeFamily(familyId, now);
            throw new UnauthenticatedException("Account is not active");
        }

        return user;
    }

    private Merchant resolveAndValidateMerchant(User user, UUID familyId, Instant now) {
        if (user.getRole() != UserRole.MERCHANT) {
            return null;
        }

        Optional<Merchant> merchantOpt = merchantRepository.findByUserId(user.getId());
        if (merchantOpt.isEmpty() || merchantOpt.get().getStatus() != MerchantStatus.APPROVED) {
            log.warn("Refresh rejected: merchant account for user {} is not APPROVED", user.getId());
            refreshTokenRepository.revokeFamily(familyId, now);
            throw new UnauthenticatedException("Merchant account is not approved");
        }

        return merchantOpt.get();
    }

    private LoginResponse buildLoginResponse(String accessToken, User user, Merchant merchant) {
        LoginUserSummary summary = new LoginUserSummary(
                user.getId(),
                user.getFullName(),
                user.getRole().name(),
                user.isMustChangePassword(),
                merchant != null ? merchant.getStatus().name() : null,
                merchant != null ? merchant.getId() : null
        );

        return new LoginResponse(
                accessToken,
                jwtService.getAccessTokenExpirySeconds(),
                summary
        );
    }

    public String generateOpaqueToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
