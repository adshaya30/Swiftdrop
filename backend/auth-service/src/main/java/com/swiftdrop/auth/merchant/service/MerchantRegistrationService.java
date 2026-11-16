package com.swiftdrop.auth.merchant.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.login.service.PasswordHasher;
import com.swiftdrop.auth.login.service.PasswordPolicy;
import com.swiftdrop.auth.merchant.dto.RegisterMerchantRequest;
import com.swiftdrop.auth.merchant.dto.RegisterMerchantResponse;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.util.PhoneNumberNormalizer;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant self-registration. SRS v2.1 - FR-AUTH-016, UC-M01, BR-12 (a new merchant starts PENDING).
 */
@Service
public class MerchantRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MerchantRegistrationService.class);

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher passwordHasher;

    public MerchantRegistrationService(UserRepository userRepository, MerchantRepository merchantRepository,
                                       PasswordPolicy passwordPolicy, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
    }

    /**
     * Creates a MERCHANT user (ACTIVE) and a merchant (PENDING) in one transaction. No tokens are issued.
     */
    @Transactional
    public RegisterMerchantResponse register(RegisterMerchantRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // BR-11 / FR-AUTH-001: phone stored as E.164
        String phone = PhoneNumberNormalizer.normalize(request.phone())
                .orElseThrow(() -> validation("phone",
                        "Must be a valid Sri Lankan phone number (e.g. 0771234567 or +94771234567)"));

        // FR-AUTH-001 password policy, shared with customer registration
        List<String> violations = passwordPolicy.check(request.password(), email);
        if (!violations.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed.",
                    violations.stream().map(v -> new FieldError("password", v)).toList());
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw emailTaken();
        }

        User user = new User();
        user.setFullName(request.contactName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordHasher.hash(request.password()));
        user.setRole(UserRole.MERCHANT);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setStatus(UserStatus.ACTIVE);

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // concurrent registration with the same email hit the unique constraint
            throw emailTaken();
        }

        Merchant merchant = new Merchant(user.getId(), request.businessName().trim(), MerchantStatus.PENDING);
        merchant.setBusinessRegistrationNo(blankToNull(request.businessRegistrationNo()));
        merchant.setAddressLine1(request.addressLine1().trim());
        merchant.setCity(request.city().trim());
        merchantRepository.save(merchant);

        // BR-28: ids only, no contact data in logs
        log.info("Merchant registered, pending approval: userId={}", user.getId());
        return RegisterMerchantResponse.pending();
    }

    private static DomainException validation(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed.",
                List.of(new FieldError(field, message)));
    }

    private static DomainException emailTaken() {
        return new DomainException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                "An account with this email address already exists.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
