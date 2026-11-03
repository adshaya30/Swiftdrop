package com.swiftdrop.auth.service;

import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.User;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service responsible for bootstrapping the initial administrator account on startup.
 * Idempotent, safe, and validates all credential requirements per SRS v2.1.
 * SRS v2.1 – FR-AUTH-015
 */
@Service
public class BootstrapAdminService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PASSWORD_COMPLEXITY =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{10,64}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxService outboxService;

    private final boolean enabled;
    private final String adminEmail;
    private final String adminName;
    private final String adminPassword;

    public BootstrapAdminService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OutboxService outboxService,
            @Value("${auth.bootstrap.enabled:true}") boolean enabled,
            @Value("${auth.bootstrap.admin-email:${BOOTSTRAP_ADMIN_EMAIL:}}") String adminEmail,
            @Value("${auth.bootstrap.admin-name:${BOOTSTRAP_ADMIN_NAME:}}") String adminName,
            @Value("${auth.bootstrap.admin-password:${BOOTSTRAP_ADMIN_PASSWORD:}}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxService = outboxService;
        this.enabled = enabled;
        this.adminEmail = adminEmail;
        this.adminName = adminName;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Bootstrap admin initialization is disabled by configuration");
            return;
        }

        bootstrapAdminIfNeeded();
    }

    /**
     * Executes the bootstrap check and creates the first admin if none exists.
     */
    @Transactional
    public void bootstrapAdminIfNeeded() {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            log.info("Administrator account already exists. Skipping bootstrap admin creation.");
            return;
        }

        log.info("No administrator account found. Initializing bootstrap administrator...");

        // 1. Mandatory credentials check
        if (adminEmail == null || adminEmail.isBlank() ||
                adminName == null || adminName.isBlank() ||
                adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap administrator credentials (BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_NAME, BOOTSTRAP_ADMIN_PASSWORD) " +
                            "must be provided when no administrator user exists in the database.");
        }

        // 2. Validate Name
        String trimmedName = adminName.trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 100) {
            throw new IllegalStateException("Bootstrap administrator name must be between 2 and 100 characters.");
        }

        // 3. Validate & Normalize Email
        String normalisedEmail = adminEmail.trim().toLowerCase();
        if (normalisedEmail.length() > 254 || !EMAIL_PATTERN.matcher(normalisedEmail).matches()) {
            throw new IllegalStateException("Bootstrap administrator email is invalid or exceeds 254 characters.");
        }

        // 4. Validate Password Complexity
        if (!PASSWORD_COMPLEXITY.matcher(adminPassword).matches()) {
            throw new IllegalStateException(
                    "Bootstrap administrator password does not meet complexity requirements " +
                            "(10-64 chars, uppercase, lowercase, digit, special char).");
        }

        // 5. Check Email Local-Part in Password
        String localPart = normalisedEmail.contains("@")
                ? normalisedEmail.substring(0, normalisedEmail.indexOf('@'))
                : normalisedEmail;
        if (!localPart.isBlank() && adminPassword.toLowerCase().contains(localPart.toLowerCase())) {
            throw new IllegalStateException("Bootstrap administrator password must not contain the email address username.");
        }

        // 6. Check if email is already taken by another role
        if (userRepository.existsByEmailIgnoreCase(normalisedEmail)) {
            throw new IllegalStateException("Bootstrap administrator email is already registered to another account: " + normalisedEmail);
        }

        // 7. Create Admin User
        User admin = new User();
        admin.setFullName(trimmedName);
        admin.setEmail(normalisedEmail);
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setAuthProvider(AuthProvider.LOCAL);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setMustChangePassword(true);

        User savedAdmin = userRepository.save(admin);

        // 8. Record audit event
        outboxService.recordAuditEvent(
                "BOOTSTRAP_ADMIN_CREATED",
                savedAdmin.getId(),
                savedAdmin.getId(),
                "ADMIN",
                Map.of("email", normalisedEmail)
        );

        log.info("Bootstrap administrator successfully created with email '{}' (mustChangePassword=true)", normalisedEmail);
    }
}

