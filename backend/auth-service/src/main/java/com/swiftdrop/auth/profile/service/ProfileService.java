package com.swiftdrop.auth.profile.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.login.service.PasswordPolicy;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import com.swiftdrop.auth.profile.dto.ChangePasswordRequest;
import com.swiftdrop.auth.profile.dto.MerchantSummaryResponse;
import com.swiftdrop.auth.profile.dto.UpdateProfileRequest;
import com.swiftdrop.auth.profile.dto.UserProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service managing user profile retrieval, profile edits, and password changes.
 * SRS v2.1 – FR-AUTH-012, FR-AUTH-013, FR-AUTH-014
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private static final Pattern E164_LK =
            Pattern.compile("^\\+94(?:7[0-9]|1[1-9]|2[1-8]|3[1-8]|4[1-7]|5[1-7]|6[3-7]|8[1-7]|9[1-2])[0-9]{7}$");

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public ProfileService(
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * Retrieves profile for the authenticated user.
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
     * Updates full name and phone number only.
     * FR-AUTH-012
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setFullName(request.fullName().trim());

        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(normalisePhone(request.phone()));
        } else {
            user.setPhone(null);
        }

        user = userRepository.save(user);
        log.info("Profile updated for userId={}", userId);

        MerchantSummaryResponse merchantSummary = null;
        if (user.getRole() == UserRole.MERCHANT) {
            merchantSummary = merchantRepository.findByUserId(userId)
                    .map(this::toMerchantSummary)
                    .orElse(null);
        }

        return toUserProfileResponse(user, merchantSummary);
    }

    /**
     * Changes password for local user, clears must_change_password flag,
     * and revokes all refresh token families.
     * FR-AUTH-013, FR-AUTH-014
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 1. Google-only accounts cannot change password via this endpoint
        if (user.getAuthProvider() == AuthProvider.GOOGLE || user.getPasswordHash() == null) {
            throw new SocialAccountPasswordChangeException();
        }

        // 2. Verify current password
        if (request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        // 3. New password must differ from current password
        if (request.currentPassword().equals(request.newPassword())) {
            throw new PasswordSameAsCurrentException();
        }

        // 4. Validate new password against PasswordPolicy
        List<String> violations = passwordPolicy.check(request.newPassword(), user.getEmail());
        if (!violations.isEmpty()) {
            throw new PasswordPolicyException(String.join(". ", violations));
        }

        // 5. Update password and reset mustChangePassword flag
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // 6. Revoke all refresh tokens for this user across all families
        refreshTokenService.revokeAllForUser(userId);
        log.info("Password changed and all refresh families revoked for userId={}", userId);
    }

    private String normalisePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String stripped = raw.strip().replaceAll("[\\s\\-()]", "");
        String e164;
        if (stripped.startsWith("+94")) {
            e164 = stripped;
        } else if (stripped.startsWith("0094")) {
            e164 = "+" + stripped.substring(2);
        } else if (stripped.startsWith("0") && stripped.length() == 10) {
            e164 = "+94" + stripped.substring(1);
        } else {
            throw new InvalidPhoneException(raw);
        }

        if (!E164_LK.matcher(e164).matches()) {
            throw new InvalidPhoneException(raw);
        }
        return e164;
    }

    private MerchantSummaryResponse toMerchantSummary(Merchant merchant) {
        return new MerchantSummaryResponse(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getStatus(),
                merchant.getDiscountPercent() != null ? merchant.getDiscountPercent() : BigDecimal.ZERO
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

