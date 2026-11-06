package com.swiftdrop.auth.users.seed;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.service.OutboxService;
import com.swiftdrop.auth.users.domain.CourierProfile;
import com.swiftdrop.auth.users.domain.CourierProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demo seed runner per FR-AUTH-025.
 * Active only when DEMO_SEED_ENABLED=true (disabled by default).
 * Seeds demo customer, approved merchant, and 3 active couriers writing courier.changed events.
 */
@Component
@Order(10)
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxService outboxService;

    private final boolean enabled;
    private final String demoPassword;

    public DemoSeedRunner(
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            CourierProfileRepository courierProfileRepository,
            PasswordEncoder passwordEncoder,
            OutboxService outboxService,
            @Value("${auth.demo-seed.enabled:${DEMO_SEED_ENABLED:false}}") boolean enabled,
            @Value("${auth.demo-seed.password:${DEMO_USER_PASSWORD:DemoPass123!}}") String demoPassword) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxService = outboxService;
        this.enabled = enabled;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("Demo data seeding is disabled");
            return;
        }

        log.info("DEMO_SEED_ENABLED=true: Seeding demo users and couriers...");
        String encodedPassword = passwordEncoder.encode(demoPassword);

        // 1. Seed Customer
        seedUserIfAbsent("customer@demo.example", "Demo Customer", UserRole.CUSTOMER, encodedPassword);

        // 2. Seed Approved Merchant
        User merchantUser = seedUserIfAbsent("merchant@demo.example", "Demo Merchant", UserRole.MERCHANT, encodedPassword);
        if (merchantUser != null && merchantRepository.findByUserId(merchantUser.getId()).isEmpty()) {
            Merchant merchant = new Merchant();
            merchant.setUserId(merchantUser.getId());
            merchant.setBusinessName("Demo QuickMart");
            merchant.setStatus(MerchantStatus.APPROVED);
            merchant.setDiscountPercent(new BigDecimal("10.00"));
            merchantRepository.save(merchant);
            log.info("Seeded demo APPROVED merchant: userId={}", merchantUser.getId());
        }

        // 3. Seed 3 Couriers (each writes courier.changed event)
        List<CourierSeedData> courierSeeds = List.of(
                new CourierSeedData("courier1@demo.example", "Kamal Perera", "MOTORBIKE", "WP-BBI-1234"),
                new CourierSeedData("courier2@demo.example", "Sunil Silva", "THREE_WHEELER", "WP-AAQ-5678"),
                new CourierSeedData("courier3@demo.example", "Nimal Fernando", "VAN", "WP-CAB-9012")
        );

        for (CourierSeedData seed : courierSeeds) {
            User courierUser = seedUserIfAbsent(seed.email, seed.fullName, UserRole.COURIER, encodedPassword);
            if (courierUser != null && courierProfileRepository.findByUserId(courierUser.getId()).isEmpty()) {
                CourierProfile profile = new CourierProfile(
                        courierUser.getId(),
                        seed.vehicleType,
                        seed.vehicleNumber,
                        "ACTIVE"
                );
                profile = courierProfileRepository.save(profile);
                outboxService.recordCourierChangedEvent(courierUser, profile);
                log.info("Seeded demo courier: email={}, profileId={}", seed.email, profile.getId());
            }
        }
    }

    private User seedUserIfAbsent(String email, String fullName, UserRole role, String encodedPassword) {
        String lowerEmail = email.toLowerCase().trim();
        var existing = userRepository.findByEmail(lowerEmail);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = new User();
        user.setEmail(lowerEmail);
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash(encodedPassword);
        user.setMustChangePassword(false);

        User saved = userRepository.save(user);
        log.info("Created demo user: email={}, role={}", lowerEmail, role);
        return saved;
    }

    private record CourierSeedData(String email, String fullName, String vehicleType, String vehicleNumber) {}
}
