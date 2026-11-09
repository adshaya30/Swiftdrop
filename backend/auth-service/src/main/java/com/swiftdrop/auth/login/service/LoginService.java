package com.swiftdrop.auth.login.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.dto.response.LoginResult;
import com.swiftdrop.auth.dto.response.LoginUserSummary;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.login.dto.IssuedRefresh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles authentication, lockout defense, and initial session issuance.
 * SRS v2.1 – FR-AUTH-003, FR-AUTH-004, FR-AUTH-005, FR-AUTH-007
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private static final long LOCKOUT_DURATION_MINUTES = 15;
    // Pre-computed BCrypt cost 12 hash used to prevent side-channel timing attacks on unknown emails
    private static final String DUMMY_BCRYPT_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public LoginService(
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Authenticates user using email and password per FR-AUTH-003/004/005.
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class})
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String normalisedEmail = request.getEmail().trim().toLowerCase();
        Instant now = Instant.now();

        // 1. Lookup user (case-insensitive)
        Optional<User> userOpt = userRepository.findByEmail(normalisedEmail);
        if (userOpt.isEmpty()) {
            // Dummy BCrypt check to prevent email enumeration timing attacks
            passwordHasher.matches(request.getPassword(), DUMMY_BCRYPT_HASH);
            log.warn("Login failed: unknown email");
            throw new InvalidCredentialsException();
        }

        User user = userOpt.get();

        // 2. Lockout check (FR-AUTH-005): checked first, even before password check
        if (user.getLockedUntil() != null) {
            if (now.isBefore(user.getLockedUntil())) {
                long retryAfter = Math.max(1, Duration.between(now, user.getLockedUntil()).getSeconds());
                log.warn("Login rejected: account locked until {} (retry-after: {}s)", user.getLockedUntil(), retryAfter);
                throw new AccountLockedException(retryAfter);
            } else {
                // Lockout window expired; clear lock
                user.setLockedUntil(null);
                user.setFailedLoginCount(0);
                userRepository.save(user);
            }
        }

        // 3. Verify password (Google-only accounts without hash do dummy check)
        boolean passwordMatches = false;
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            passwordMatches = passwordHasher.matches(request.getPassword(), user.getPasswordHash());
        } else {
            passwordHasher.matches(request.getPassword(), DUMMY_BCRYPT_HASH);
        }

        if (!passwordMatches) {
            log.warn("Login failed: invalid password or passwordless account for userId={}", user.getId());
            Instant lockTime = now.plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES);
            userRepository.recordFailedLoginAttempt(user.getId(), lockTime);
            throw new InvalidCredentialsException();
        }

        // 4. Verify account status (FR-AUTH-004) - only after valid credentials
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            log.warn("Login rejected: user {} is DEACTIVATED", user.getId());
            throw new AccountDeactivatedException();
        }

        // 5. Merchant approval gate (FR-AUTH-003, FR-AUTH-004)
        UUID merchantId = null;
        String merchantStatus = null;
        Merchant merchant = null;
        if (user.getRole() == UserRole.MERCHANT) {
            Optional<Merchant> merchantOpt = merchantRepository.findByUserId(user.getId());
            if (merchantOpt.isPresent()) {
                merchant = merchantOpt.get();
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

        // 6. Reset failed login counter and set last login time
        userRepository.resetFailedLoginLock(user.getId(), now);

        // 7. Issue RS256 JWT access token
        String accessToken = jwtService.issueUserToken(user, merchant);

        // 8. Issue 256-bit refresh token family
        IssuedRefresh issuedRefresh = refreshTokenService.issue(user);

        // 9. Assemble response
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
                jwtService.getAccessTokenExpirySeconds(),
                userSummary
        );

        log.info("User logged in successfully userId={}, role={}", user.getId(), user.getRole());
        return new LoginResult(response, issuedRefresh.rawToken());
    }
}
