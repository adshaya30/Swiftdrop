package com.swiftdrop.auth.users.bootstrap;

import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.login.service.PasswordPolicy;
import com.swiftdrop.auth.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Bootstraps initial administrator account at startup per FR-AUTH-015 and DEP-010.
 * Idempotent: runs only if no ADMIN user exists. Enforces PasswordPolicy strictly.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final OutboxService outboxService;

    private final boolean enabled;
    private final String adminEmail;
    private final String adminName;
    private final String adminPassword;

    public BootstrapAdminRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            OutboxService outboxService,
            @Value("${auth.bootstrap.enabled:true}") boolean enabled,
            @Value("${auth.bootstrap.admin-email:${BOOTSTRAP_ADMIN_EMAIL:}}") String adminEmail,
            @Value("${auth.bootstrap.admin-name:${BOOTSTRAP_ADMIN_NAME:}}") String adminName,
            @Value("${auth.bootstrap.admin-password:${BOOTSTRAP_ADMIN_PASSWORD:}}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
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
            log.info("Bootstrap admin initialization is disabled");
            return;
        }

        if (userRepository.existsByRole(UserRole.ADMIN)) {
            log.info("Administrator user already exists in database. Skipping bootstrap admin.");
            return;
        }

        log.info("No administrator found in database. Initializing bootstrap administrator...");

        if (adminEmail == null || adminEmail.isBlank() ||
                adminName == null || adminName.isBlank() ||
                adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap admin configuration missing: BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_NAME, and " +
                            "BOOTSTRAP_ADMIN_PASSWORD must all be configured when no administrator exists.");
        }

        String email = adminEmail.trim().toLowerCase();
        String name = adminName.trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new IllegalStateException("Bootstrap administrator name must be between 2 and 100 characters.");
        }

        List<String> policyViolations = passwordPolicy.check(adminPassword, email);
        if (!policyViolations.isEmpty()) {
            throw new IllegalStateException(
                    "Bootstrap admin password does not satisfy password policy: " + String.join("; ", policyViolations));
        }

        User adminUser = new User();
        adminUser.setEmail(email);
        adminUser.setFullName(name);
        adminUser.setPasswordHash(passwordEncoder.encode(adminPassword));
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setAuthProvider(AuthProvider.LOCAL);
        adminUser.setMustChangePassword(true);

        User savedAdmin = userRepository.save(adminUser);

        outboxService.recordAuditEvent(
                "USER_CREATED",
                savedAdmin.getId(),
                savedAdmin.getId(),
                "ADMIN",
                Map.of("bootstrap", true)
        );

        log.info("Bootstrap administrator account created successfully: email={}", email);
    }
}

