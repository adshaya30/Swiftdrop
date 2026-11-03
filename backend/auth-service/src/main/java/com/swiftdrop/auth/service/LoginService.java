package com.swiftdrop.auth.service;

import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.dto.response.LoginResult;
import com.swiftdrop.auth.dto.response.LoginUserSummary;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.repository.MerchantRepository;
import com.swiftdrop.auth.repository.RefreshTokenRepository;
import com.swiftdrop.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Handles email/password authentication, account lockout, and initial session issuance.
 * SRS v2.1 – FR-AUTH-003, FR-AUTH-004, FR-AUTH-005, FR-AUTH-007
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 15;
    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 7;

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginService(
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Authenticates a user using email and password.
     *
     * @param request   validated login request
     * @param ipAddress client IP address
     * @param userAgent client User-Agent header
     * @return login result containing response body and raw refresh token
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class})
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String normalisedEmail = request.getEmail().trim().toLowerCase();

        // 1. Lookup user (case-insensitive)
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalisedEmail);
        if (userOpt.isEmpty()) {
            log.warn("Login failed: email not found");
            throw new InvalidCredentialsException();
        }

        User user = userOpt.get();

        // 2. Account lockout check (FR-AUTH-005)
        // Locked accounts remain locked even if the correct password is provided.
        Instant now = Instant.now();
        if (user.getLockedUntil() != null) {
            if (now.isBefore(user.getLockedUntil())) {
                long retryAfter = Math.max(1, Duration.between(now, user.getLockedUntil()).getSeconds());
                log.warn("Login rejected: account locked until {} (remaining {}s)", user.getLockedUntil(), retryAfter);
                throw new AccountLockedException(retryAfter);
            } else {
                // Lockout window has passed; clear the lock
                user.setLockedUntil(null);
                user.setFailedLoginCount(0);
                userRepository.save(user);
            }
        }

        // 3. Verify password
        boolean passwordMatches = false;
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        }

        if (!passwordMatches) {
            log.warn("Login failed: invalid password or passwordless account for userId={}", user.getId());
            Instant lockTime = now.plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES);
            userRepository.recordFailedLoginAttempt(user.getId(), lockTime);
            throw new InvalidCredentialsException();
        }

        // 4. Verify account status (FR-AUTH-004)
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            log.warn("Login rejected: user {} is DEACTIVATED", user.getId());
            throw new AccountDeactivatedException();
        }

        // 5. Merchant approval gate (FR-AUTH-003, FR-AUTH-004)
        UUID merchantId = null;
        String merchantStatus = null;
        if (user.getRole() == UserRole.MERCHANT) {
            Optional<Merchant> merchantOpt = merchantRepository.findByUserId(user.getId());
            if (merchantOpt.isPresent()) {
                Merchant merchant = merchantOpt.get();
                merchantId = merchant.getId();
                merchantStatus = merchant.getStatus().name();

                if (merchant.getStatus() == MerchantStatus.PENDING) {
                    throw new MerchantPendingException();
                } else if (merchant.getStatus() == MerchantStatus.REJECTED) {
                    throw new MerchantRejectedException();
                } else if (merchant.getStatus() == MerchantStatus.SUSPENDED) {
                    throw new MerchantSuspendedException();
                }
            }
        }

        // 6. Reset failed login counter and set last login time on successful authentication
        userRepository.resetFailedLoginLock(user.getId(), now);

        // 7. Issue RS256 JWT access token (FR-AUTH-003, FR-AUTH-006, Section 3.6)
        String accessToken = jwtTokenService.issueAccessToken(
                user.getId(),
                user.getRole().name(),
                user.isMustChangePassword(),
                merchantId
        );

        // 8. Issue 256-bit opaque refresh token & store only its SHA-256 hash (FR-AUTH-007)
        String rawRefreshToken = generateOpaqueToken();
        String tokenHash = sha256Hex(rawRefreshToken);
        UUID familyId = UUID.randomUUID();

        RefreshToken session = new RefreshToken(
                user.getId(),
                familyId,
                tokenHash,
                now.plus(REFRESH_TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS),
                userAgent,
                ipAddress
        );
        refreshTokenRepository.save(session);

        // 9. Assemble response (FR-AUTH-003)
        LoginUserSummary userSummary = new LoginUserSummary(
                user.getId(),
                user.getFullName(),
                user.getRole().name(),
                user.isMustChangePassword(),
                merchantStatus,
                merchantId
        );

        LoginResponse response = new LoginResponse(
                accessToken,
                JwtTokenService.ACCESS_TOKEN_EXPIRY_SECONDS,
                userSummary
        );

        log.info("User logged in successfully userId={}, role={}", user.getId(), user.getRole());
        return new LoginResult(response, rawRefreshToken);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32]; // 256-bit random
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
