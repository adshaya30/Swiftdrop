package com.swiftdrop.auth.service;

import com.swiftdrop.auth.dto.request.RegistrationRequest;
import com.swiftdrop.auth.dto.response.UserResponse;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.exception.EmailAlreadyRegisteredException;
import com.swiftdrop.auth.exception.InvalidPhoneException;
import com.swiftdrop.auth.exception.PasswordPolicyException;
import com.swiftdrop.auth.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Business logic for customer registration.
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-002
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    /**
     * Validates the normalised phone number against the SRS-required pattern.
     * E.164 Sri Lankan numbers: +94 followed by a non-zero digit then 8 digits.
     */
    private static final Pattern E164_LK = Pattern.compile("^\\+94[1-9][0-9]{8}$");

    /**
     * Password complexity: at least one uppercase, one lowercase, one digit, one special char.
     */
    private static final Pattern PASSWORD_COMPLEXITY =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{10,64}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new customer.
     *
     * @param request validated registration request
     * @return safe user summary (no password/token)
     */
    @Transactional
    public UserResponse register(RegistrationRequest request) {

        // 1. Normalise email (lowercase + trim)
        String normalisedEmail = request.getEmail().trim().toLowerCase();

        // 2. Normalise phone to E.164 and validate
        String normalisedPhone = normalisePhone(request.getPhone());

        // 3. Validate password complexity and ensure it doesn't contain email local-part
        validatePassword(request.getPassword(), normalisedEmail);

        // 4. Check for duplicate email (pre-check; DB constraint is the authoritative guard)
        if (userRepository.existsByEmailIgnoreCase(normalisedEmail)) {
            throw new EmailAlreadyRegisteredException(normalisedEmail);
        }

        // 5. Hash password with BCrypt cost 12
        String hash = passwordEncoder.encode(request.getPassword());

        // 6. Build user – server enforces CUSTOMER + LOCAL, never trusts client
        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(normalisedEmail);
        user.setPhone(normalisedPhone);
        user.setPasswordHash(hash);
        user.setRole(UserRole.CUSTOMER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setStatus(UserStatus.ACTIVE);

        // 7. Persist – DB unique constraint on CITEXT email is the final concurrency guard
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent registration with the same email hit the DB unique constraint
            log.warn("Concurrent duplicate registration for email (normalised)", ex);
            throw new EmailAlreadyRegisteredException(normalisedEmail);
        }

        log.info("Customer registered successfully id={}", user.getId());

        return toResponse(user);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Normalises a Sri Lankan phone number to E.164 (+94XXXXXXXXX).
     * Accepted input formats (SRS FR-AUTH-001):
     * <ul>
     *   <li>07X-XXX-XXXX  (with or without hyphens/spaces)</li>
     *   <li>07XXXXXXXX</li>
     *   <li>+947XXXXXXXX</li>
     *   <li>00947XXXXXXXX</li>
     * </ul>
     */
    String normalisePhone(String raw) {
        if (raw == null) throw new InvalidPhoneException("null");

        // Strip all non-digit characters except leading '+'
        String stripped = raw.strip()
                .replaceAll("[\\s\\-()]", ""); // remove spaces, hyphens, parens

        String e164;
        if (stripped.startsWith("+94")) {
            // Already has country code with +
            e164 = stripped;
        } else if (stripped.startsWith("0094")) {
            // International prefix 0094
            e164 = "+" + stripped.substring(2);
        } else if (stripped.startsWith("0") && stripped.length() == 10) {
            // Local format: 0XXXXXXXXX → +94XXXXXXXXX
            e164 = "+94" + stripped.substring(1);
        } else {
            throw new InvalidPhoneException(raw);
        }

        if (!E164_LK.matcher(e164).matches()) {
            throw new InvalidPhoneException(raw);
        }
        return e164;
    }

    /**
     * Validates password complexity rules from SRS FR-AUTH-001.
     * Throws {@link org.springframework.validation.annotation.Validated} compatible
     * exception caught by the global handler.
     */
    void validatePassword(String password, String normalisedEmail) {
        if (!PASSWORD_COMPLEXITY.matcher(password).matches()) {
            throw new PasswordPolicyException(
                    "Password must be 10–64 characters and contain at least one uppercase letter, " +
                            "lowercase letter, digit, and special character.");
        }

        // Must not contain the email local-part (part before @)
        String localPart = normalisedEmail.contains("@")
                ? normalisedEmail.substring(0, normalisedEmail.indexOf('@'))
                : normalisedEmail;

        if (!localPart.isBlank() && password.toLowerCase().contains(localPart.toLowerCase())) {
            throw new PasswordPolicyException("Password must not contain your email address.");
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.getAuthProvider().name(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
