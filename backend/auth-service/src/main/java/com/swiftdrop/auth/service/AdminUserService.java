package com.swiftdrop.auth.service;

import com.swiftdrop.auth.client.DeliveryServiceClient;
import com.swiftdrop.auth.dto.admin.*;
import com.swiftdrop.auth.dto.response.MerchantSummaryResponse;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.repository.CourierProfileRepository;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.util.SecurePasswordGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing administrative user operations: creation, search, edit, activation,
 * deactivation with cross-service delivery check, and password reset.
 * SRS v2.1 – FR-AUTH-020, FR-AUTH-021, Flow 4.6
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final MerchantRepository merchantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeliveryServiceClient deliveryServiceClient;
    private final OutboxService outboxService;

    public AdminUserService(
            UserRepository userRepository,
            CourierProfileRepository courierProfileRepository,
            MerchantRepository merchantRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            DeliveryServiceClient deliveryServiceClient,
            OutboxService outboxService) {
        this.userRepository = userRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.merchantRepository = merchantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.deliveryServiceClient = deliveryServiceClient;
        this.outboxService = outboxService;
    }

    /**
     * Lists users with optional role, status, and search filters with pagination.
     * SRS v2.1 – FR-AUTH-020
     */
    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> listUsers(UserRole role, UserStatus status, String query, Pageable pageable) {
        Specification<User> spec = (root, q, cb) -> {
            var predicates = cb.conjunction();

            if (role != null) {
                predicates = cb.and(predicates, cb.equal(root.get("role"), role));
            }

            if (status != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            }

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                var nameLike = cb.like(cb.lower(root.get("fullName")), pattern);
                var emailLike = cb.like(cb.lower(root.get("email")), pattern);
                predicates = cb.and(predicates, cb.or(nameLike, emailLike));
            }

            return predicates;
        };

        return userRepository.findAll(spec, pageable).map(this::toSummaryResponse);
    }

    /**
     * Creates an ADMIN or COURIER user and generates a 16-character temporary password.
     * Emits courier.changed and audit.recorded outbox events transactionally.
     * SRS v2.1 – FR-AUTH-020
     */
    @Transactional
    public CreateUserResponse createUser(UUID actorId, CreateUserRequest request) {
        UserRole targetRole;
        try {
            targetRole = UserRole.valueOf(request.role().trim().toUpperCase());
        } catch (Exception e) {
            throw new InvalidUserRoleException("Invalid role requested: " + request.role());
        }

        if (targetRole != UserRole.ADMIN && targetRole != UserRole.COURIER) {
            throw new InvalidUserRoleException("Only ADMIN and COURIER users can be created via admin user management.");
        }

        if (targetRole == UserRole.COURIER) {
            if (request.vehicleType() == null || request.vehicleType().isBlank()) {
                throw new IllegalArgumentException("Vehicle type is required for courier accounts.");
            }
            if (request.vehicleNumber() == null || request.vehicleNumber().isBlank()) {
                throw new IllegalArgumentException("Vehicle number is required for courier accounts.");
            }
        }

        String normalisedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalisedEmail)) {
            throw new EmailAlreadyRegisteredException("An account with this email address already exists.");
        }

        String tempPassword = SecurePasswordGenerator.generateTemporaryPassword(normalisedEmail);

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalisedEmail);
        user.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        user.setRole(targetRole);
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);

        User savedUser = userRepository.save(user);

        CourierProfile courierProfile = null;
        if (targetRole == UserRole.COURIER) {
            CourierProfile cp = new CourierProfile(
                    savedUser.getId(),
                    request.vehicleType().trim(),
                    request.vehicleNumber().trim(),
                    "ACTIVE"
            );
            courierProfile = courierProfileRepository.save(cp);
            outboxService.recordCourierChangedEvent(savedUser, courierProfile);
        }

        outboxService.recordAuditEvent(
                "USER_CREATED",
                actorId,
                savedUser.getId(),
                targetRole.name(),
                Map.of("email", normalisedEmail)
        );

        log.info("Admin {} created user {} with role {}", actorId, savedUser.getId(), targetRole);
        return new CreateUserResponse(toSummaryResponse(savedUser, courierProfile), tempPassword);
    }

    /**
     * Retrieves safe user details.
     * SRS v2.1 – FR-AUTH-020
     */
    @Transactional(readOnly = true)
    public AdminUserSummaryResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toSummaryResponse(user);
    }

    /**
     * Updates permitted user details (fullName, phone, vehicle details for couriers).
     * Rejects updates to role, email, passwordHash, status, authProvider, googleSub.
     * SRS v2.1 – FR-AUTH-020
     */
    @Transactional
    public AdminUserSummaryResponse editUser(UUID actorId, UUID userId, EditUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }

        if (request.phone() != null) {
            user.setPhone(!request.phone().isBlank() ? request.phone().trim() : null);
        }

        User updatedUser = userRepository.save(user);
        CourierProfile courierProfile = null;

        if (user.getRole() == UserRole.COURIER) {
            courierProfile = courierProfileRepository.findByUserId(userId).orElse(null);
            if (courierProfile != null) {
                boolean profileModified = false;
                if (request.vehicleType() != null && !request.vehicleType().isBlank()) {
                    courierProfile.setVehicleType(request.vehicleType().trim());
                    profileModified = true;
                }
                if (request.vehicleNumber() != null && !request.vehicleNumber().isBlank()) {
                    courierProfile.setVehicleNumber(request.vehicleNumber().trim());
                    profileModified = true;
                }
                if (profileModified) {
                    courierProfile = courierProfileRepository.save(courierProfile);
                    outboxService.recordCourierChangedEvent(updatedUser, courierProfile);
                }
            }
        }

        outboxService.recordAuditEvent(
                "USER_UPDATED",
                actorId,
                userId,
                user.getRole().name(),
                Map.of("fullName", updatedUser.getFullName())
        );

        log.info("Admin {} updated user {}", actorId, userId);
        return toSummaryResponse(updatedUser, courierProfile);
    }

    /**
     * Activates a user account.
     * SRS v2.1 – FR-AUTH-020
     */
    @Transactional
    public AdminUserSummaryResponse activateUser(UUID actorId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new InvalidUserStatusTransitionException("User is already ACTIVE.");
        }

        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);

        CourierProfile courierProfile = null;
        if (user.getRole() == UserRole.COURIER) {
            courierProfile = courierProfileRepository.findByUserId(userId).orElse(null);
            if (courierProfile != null) {
                courierProfile.setStatus("ACTIVE");
                courierProfile = courierProfileRepository.save(courierProfile);
                outboxService.recordCourierChangedEvent(savedUser, courierProfile);
            }
        }

        outboxService.recordAuditEvent("USER_ACTIVATED", actorId, userId, user.getRole().name(), Map.of());
        log.info("Admin {} activated user {}", actorId, userId);
        return toSummaryResponse(savedUser, courierProfile);
    }

    /**
     * Deactivates a user account.
     * Enforces self-deactivation guard and cross-service delivery check for couriers.
     * Revokes all active refresh tokens for the deactivated user.
     * SRS v2.1 – Flow 4.6, FR-AUTH-020
     */
    @Transactional
    public AdminUserSummaryResponse deactivateUser(UUID actorId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Self-deactivation guard
        if (actorId != null && actorId.equals(userId)) {
            throw new CannotDeactivateSelfException();
        }

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new InvalidUserStatusTransitionException("User is already DEACTIVATED.");
        }

        CourierProfile courierProfile = null;
        if (user.getRole() == UserRole.COURIER) {
            courierProfile = courierProfileRepository.findByUserId(userId).orElse(null);
            UUID courierIdToCheck = courierProfile != null ? courierProfile.getId() : userId;

            // Call delivery-service cross-service dependency (Flow 4.6)
            int activeDeliveries = deliveryServiceClient.getActiveDeliveriesCount(courierIdToCheck);
            if (activeDeliveries > 0) {
                log.warn("Cannot deactivate courier {}: {} active deliveries in progress", userId, activeDeliveries);
                throw new CourierHasActiveDeliveriesException(activeDeliveries);
            }

            if (courierProfile != null) {
                courierProfile.setStatus("DEACTIVATED");
                courierProfile = courierProfileRepository.save(courierProfile);
                outboxService.recordCourierChangedEvent(user, courierProfile);
            }
        }

        user.setStatus(UserStatus.DEACTIVATED);
        User savedUser = userRepository.save(user);

        // Revoke all active refresh tokens
        int revokedCount = refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("Revoked {} refresh token(s) upon deactivating user {}", revokedCount, userId);

        outboxService.recordAuditEvent("USER_DEACTIVATED", actorId, userId, user.getRole().name(), Map.of());
        log.info("Admin {} deactivated user {}", actorId, userId);
        return toSummaryResponse(savedUser, courierProfile);
    }

    /**
     * Resets a user's password, generating a 16-character temporary password.
     * Forces password change on next login and revokes all refresh token sessions.
     * SRS v2.1 – FR-AUTH-021
     */
    @Transactional
    public ResetPasswordResponse resetPassword(UUID actorId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getAuthProvider() == AuthProvider.GOOGLE || user.getPasswordHash() == null) {
            throw new SocialAccountPasswordChangeException();
        }

        String tempPassword = SecurePasswordGenerator.generateTemporaryPassword(user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        userRepository.saveAndFlush(user);

        // Revoke all refresh token sessions
        int revokedCount = refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("Password reset by admin {} for user {}. Revoked {} refresh token(s).", actorId, userId, revokedCount);

        // Record audit event WITHOUT temporary password
        outboxService.recordAuditEvent(
                "PASSWORD_RESET",
                actorId,
                userId,
                user.getRole().name(),
                Map.of("targetEmail", user.getEmail())
        );

        return new ResetPasswordResponse(
                tempPassword,
                "Password reset successfully. The temporary password must be changed upon next login."
        );
    }

    private AdminUserSummaryResponse toSummaryResponse(User user) {
        CourierProfile courierProfile = null;
        if (user.getRole() == UserRole.COURIER) {
            courierProfile = courierProfileRepository.findByUserId(user.getId()).orElse(null);
        }
        return toSummaryResponse(user, courierProfile);
    }

    private AdminUserSummaryResponse toSummaryResponse(User user, CourierProfile courierProfile) {
        CourierProfileSummaryResponse cpResponse = null;
        if (courierProfile != null) {
            cpResponse = new CourierProfileSummaryResponse(
                    courierProfile.getId(),
                    courierProfile.getVehicleType(),
                    courierProfile.getVehicleNumber(),
                    courierProfile.getStatus()
            );
        }

        MerchantSummaryResponse merchantSummary = null;
        if (user.getRole() == UserRole.MERCHANT) {
            merchantSummary = merchantRepository.findByUserId(user.getId())
                    .map(m -> new MerchantSummaryResponse(m.getId(), m.getBusinessName(), m.getStatus(), BigDecimal.ZERO))
                    .orElse(null);
        }

        return new AdminUserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getAuthProvider(),
                user.isMustChangePassword(),
                user.getFailedLoginCount(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                cpResponse,
                merchantSummary
        );
    }
}

