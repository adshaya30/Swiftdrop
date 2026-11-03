package com.swiftdrop.auth.service;

import com.swiftdrop.auth.dto.request.ChangePasswordRequest;
import com.swiftdrop.auth.dto.request.UpdateProfileRequest;
import com.swiftdrop.auth.dto.response.MerchantSummaryResponse;
import com.swiftdrop.auth.dto.response.UserProfileResponse;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.Merchant;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.repository.MerchantRepository;
import com.swiftdrop.auth.repository.RefreshTokenRepository;
import com.swiftdrop.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service managing profile retrieval, profile updates, and password changes.
 * SRS v2.1 – FR-AUTH-012, FR-AUTH-013, FR-AUTH-014
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private static final Pattern PASSWORD_COMPLEXITY =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{10,64}$");

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Retrieves the profile of the authenticated user.
     * FR-AUTH-012
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        MerchantSummaryResponse merchantSummary = null;
        if (user.getRole() == UserRole.MERCHANT) {
            merchantSummary = merchantRepository.findByUserId(userId)
                    .map(this::toMerchantSummary)
                    .orElse(null);
        }

        return toUserProfileResponse(user, merchantSummary);
    }

    /**
     * Updates the full name and phone number of the authenticated user.
     * Modifying role, email, status, or other fields is rejected / not exposed.
     * FR-AUTH-012
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setFullName(request.fullName().trim());

        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        } else {
            user.setPhone(null);
        }

        user = userRepository.save(user);
        log.info("Profile updated successfully for user {}", userId);

        MerchantSummaryResponse merchantSummary = null;
        if (user.getRole() == UserRole.MERCHANT) {
            merchantSummary = merchantRepository.findByUserId(userId)
                    .map(this::toMerchantSummary)
                    .orElse(null);
        }

        return toUserProfileResponse(user, merchantSummary);
    }

    /**
     * Changes the password for a local user.
     * Revokes all active refresh tokens for the user and clears forced password change flag.
     * FR-AUTH-013, FR-AUTH-014
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 1. Rejection of OAuth accounts
        if (user.getAuthProvider() == AuthProvider.GOOGLE || user.getPasswordHash() == null) {
            throw new SocialAccountPasswordChangeException();
        }

        // 2. Verify current password
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        // 3. New password must be different from current password
        if (request.currentPassword().equals(request.newPassword())) {
            throw new PasswordSameAsCurrentException();
        }

        // 4. Complexity & email local-part check
        validateNewPassword(request.newPassword(), user.getEmail());

        // 5. Update password and reset mustChangePassword flag
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.saveAndFlush(user);

        // 6. Revoke all active refresh tokens for this user
        int revokedCount = refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("Password changed for user {}. Revoked {} active refresh token(s).", userId, revokedCount);
    }

    private void validateNewPassword(String newPassword, String email) {
        if (!PASSWORD_COMPLEXITY.matcher(newPassword).matches()) {
            throw new PasswordPolicyException(
                    "Password must be 10–64 characters and contain at least one uppercase letter, " +
                            "lowercase letter, digit, and special character.");
        }

        String localPart = email.contains("@")
                ? email.substring(0, email.indexOf('@'))
                : email;

        if (!localPart.isBlank() && newPassword.toLowerCase().contains(localPart.toLowerCase())) {
            throw new PasswordPolicyException("Password must not contain your email address.");
        }
    }

    private MerchantSummaryResponse toMerchantSummary(Merchant merchant) {
        return new MerchantSummaryResponse(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getStatus(),
                BigDecimal.ZERO
        );
    }

    private UserProfileResponse toUserProfileResponse(User user, MerchantSummaryResponse merchant) {
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getAuthProvider(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                merchant
        );
    }
}
