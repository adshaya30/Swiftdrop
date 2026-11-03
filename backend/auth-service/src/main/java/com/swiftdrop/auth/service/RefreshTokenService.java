package com.swiftdrop.auth.service;

import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.dto.response.LoginUserSummary;
import com.swiftdrop.auth.dto.response.RefreshResult;
import com.swiftdrop.auth.entity.Merchant;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.RefreshToken;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.exception.UnauthenticatedException;
import com.swiftdrop.auth.repository.MerchantRepository;
import com.swiftdrop.auth.repository.RefreshTokenRepository;
import com.swiftdrop.auth.repository.UserRepository;
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
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Refresh Token Rotation (FR-AUTH-007) and Logout (FR-AUTH-008).
 * Enforces token family replay detection, 10s grace interval, 30-day max family lifetime,
 * and database-backed concurrency safety.
 * SRS v2.1 – FR-AUTH-007, FR-AUTH-008, Section 3.6, Section 6.3
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    public static final long REFRESH_TOKEN_VALIDITY_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final int graceSeconds;
    private final int familyMaxDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            JwtTokenService jwtTokenService,
            @Value("${refresh.grace-seconds:10}") int graceSeconds,
            @Value("${refresh.family-max-days:30}") int familyMaxDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.jwtTokenService = jwtTokenService;
        this.graceSeconds = graceSeconds;
        this.familyMaxDays = familyMaxDays;
    }

    /**
     * Rotates an existing refresh token:
     * - Verifies token hash, validity, expiration, and family age.
     * - Detects token reuse/replay:
     *     - Within graceSeconds -> returns new access token, no new cookie/refresh token.
     *     - After graceSeconds -> revokes entire token family (compromise detected).
     * - On normal rotation -> marks old token as used, issues new 256-bit refresh token in same family,
     *   and returns new access JWT.
     *
     * @param rawRefreshToken opaque token string from sd_refresh cookie
     * @param userAgent       client User-Agent
     * @param ipAddress       client IP
     * @return RefreshResult containing LoginResponse and optional new raw refresh token
     */
    @Transactional(noRollbackFor = {UnauthenticatedException.class})
    public RefreshResult rotate(String rawRefreshToken, String userAgent, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            log.warn("Refresh failed: missing refresh token");
            throw new UnauthenticatedException("Refresh token is missing");
        }

        String tokenHash = sha256Hex(rawRefreshToken);
        Instant now = Instant.now();

        // 1. Find token with row-level pessimistic lock (SELECT ... FOR UPDATE) to prevent concurrency races
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHashWithLock(tokenHash);
        if (tokenOpt.isEmpty()) {
            log.warn("Refresh failed: unknown token hash");
            throw new UnauthenticatedException("Invalid refresh token");
        }

        RefreshToken token = tokenOpt.get();

        // 2. Check if the token or its entire family has already been revoked
        if (token.getRevokedAt() != null || refreshTokenRepository.isFamilyRevoked(token.getFamilyId())) {
            log.warn("Refresh rejected: token family {} is already revoked", token.getFamilyId());
            throw new UnauthenticatedException("Refresh token family has been revoked");
        }

        // 3. Check expiration (7-day window)
        if (now.isAfter(token.getExpiresAt())) {
            log.warn("Refresh rejected: token expired at {}", token.getExpiresAt());
            throw new UnauthenticatedException("Refresh token has expired");
        }

        // 4. Enforce 30-day absolute maximum lifetime of the token family (SRS v2.1 FR-AUTH-007)
        Instant familyAbsoluteExpiry = token.getFamilyCreatedAt().plus(familyMaxDays, ChronoUnit.DAYS);
        if (!now.isBefore(familyAbsoluteExpiry)) {
            log.warn("Refresh rejected: token family {} exceeded max lifetime of {} days", token.getFamilyId(), familyMaxDays);
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            throw new UnauthenticatedException("Token family lifetime expired (maximum " + familyMaxDays + " days)");
        }

        // 5. Check if the token has already been used (Replay Detection / Concurrent Tabs)
        if (token.getUsedAt() != null) {
            long secondsSinceUsed = Duration.between(token.getUsedAt(), now).getSeconds();
            if (secondsSinceUsed <= graceSeconds) {
                // Grace window for concurrent requests / two-tab race:
                // Return fresh access token only, DO NOT issue another refresh token or cookie
                log.info("Refresh concurrent-tab grace hit ({}s <= {}s) for family {}", secondsSinceUsed, graceSeconds, token.getFamilyId());
                User user = validateActiveUser(token.getUserId(), token.getFamilyId(), now);
                Merchant merchant = resolveAndValidateMerchant(user, token.getFamilyId(), now);

                String accessToken = jwtTokenService.issueAccessToken(
                        user.getId(),
                        user.getRole().name(),
                        user.isMustChangePassword(),
                        merchant != null ? merchant.getId() : null
                );

                LoginUserSummary summary = new LoginUserSummary(
                        user.getId(),
                        user.getFullName(),
                        user.getRole().name(),
                        user.isMustChangePassword(),
                        merchant != null ? merchant.getStatus().name() : null,
                        merchant != null ? merchant.getId() : null
                );

                LoginResponse response = new LoginResponse(
                        accessToken,
                        JwtTokenService.ACCESS_TOKEN_EXPIRY_SECONDS,
                        summary
                );

                // newRawRefreshToken is null -> AuthController will NOT set a new refresh cookie
                return new RefreshResult(response, null);
            } else {
                // Replay detected after grace window!
                log.warn("SECURITY ALERT: Refresh token replay detected! Token used {}s ago (> {}s). Revoking entire family {}",
                        secondsSinceUsed, graceSeconds, token.getFamilyId());
                refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
                throw new UnauthenticatedException("Refresh token replay detected. Session terminated.");
            }
        }

        // 6. Normal rotation: mark presented token as used
        token.setUsedAt(now);
        refreshTokenRepository.save(token);

        // 7. Verify user and merchant status
        User user = validateActiveUser(token.getUserId(), token.getFamilyId(), now);
        Merchant merchant = resolveAndValidateMerchant(user, token.getFamilyId(), now);

        // 8. Generate new 256-bit cryptographically secure random opaque token
        String newRawRefreshToken = generateOpaqueToken();
        String newTokenHash = sha256Hex(newRawRefreshToken);

        // 9. Save new refresh token in the SAME family, preserving original family_created_at
        RefreshToken newToken = new RefreshToken();
        newToken.setUserId(user.getId());
        newToken.setFamilyId(token.getFamilyId()); // Same family!
        newToken.setFamilyCreatedAt(token.getFamilyCreatedAt()); // Preserves family start!
        newToken.setTokenHash(newTokenHash);
        Instant standardExpiry = now.plus(REFRESH_TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS);
        Instant effectiveExpiry = standardExpiry.isBefore(familyAbsoluteExpiry) ? standardExpiry : familyAbsoluteExpiry;
        newToken.setExpiresAt(effectiveExpiry);
        newToken.setCreatedAt(now);
        newToken.setIssuedAt(now);
        newToken.setUserAgent(userAgent);
        newToken.setIpAddress(ipAddress);
        refreshTokenRepository.save(newToken);

        // 10. Issue fresh RS256 access JWT
        String accessToken = jwtTokenService.issueAccessToken(
                user.getId(),
                user.getRole().name(),
                user.isMustChangePassword(),
                merchant != null ? merchant.getId() : null
        );

        LoginUserSummary summary = new LoginUserSummary(
                user.getId(),
                user.getFullName(),
                user.getRole().name(),
                user.isMustChangePassword(),
                merchant != null ? merchant.getStatus().name() : null,
                merchant != null ? merchant.getId() : null
        );

        LoginResponse response = new LoginResponse(
                accessToken,
                JwtTokenService.ACCESS_TOKEN_EXPIRY_SECONDS,
                summary
        );

        log.info("Refresh token rotated successfully for userId={}, familyId={}", user.getId(), token.getFamilyId());
        return new RefreshResult(response, newRawRefreshToken);
    }

    /**
     * Revokes the entire refresh token family corresponding to the given raw token (FR-AUTH-008).
     *
     * @param rawRefreshToken raw token from cookie (may be null/empty)
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = sha256Hex(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
            log.info("User logout: revoked refresh familyId={}", token.getFamilyId());
        } else {
            log.debug("Logout called with unrecognized refresh token hash");
        }
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

    public String generateOpaqueToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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
