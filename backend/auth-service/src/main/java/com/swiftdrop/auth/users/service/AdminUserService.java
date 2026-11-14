package com.swiftdrop.auth.users.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import com.swiftdrop.auth.profile.dto.MerchantSummaryResponse;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.common.events.data.CourierChangedData;
import com.swiftdrop.auth.users.client.DeliveryClient;
import com.swiftdrop.auth.users.domain.CourierProfile;
import com.swiftdrop.auth.users.domain.CourierProfileRepository;
import com.swiftdrop.auth.users.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing administrative user operations: creation, search, edit, activation,
 * deactivation with cross-service delivery check, and password reset.
 * SRS v2.1 – FR-AUTH-020, FR-AUTH-021, Flow 5
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final Set<String> ALLOWED_VEHICLE_TYPES = Set.of("MOTORBIKE", "THREE_WHEELER", "VAN");

    private final UserRepository userRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final MerchantRepository merchantRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final DeliveryClient deliveryClient;
    private final OutboxService outboxService;
    private final AuditEvents auditEvents;

    public AdminUserService(
            UserRepository userRepository,
            CourierProfileRepository courierProfileRepository,
            MerchantRepository merchantRepository,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            DeliveryClient deliveryClient,
            OutboxService outboxService,
            AuditEvents auditEvents) {
        this.userRepository = userRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.merchantRepository = merchantRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.deliveryClient = deliveryClient;
        this.outboxService = outboxService;
        this.auditEvents = auditEvents;
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
     * Retrieves single user details by ID.
     */
    @Transactional(readOnly = true)
    public AdminUserSummaryResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toSummaryResponse(user);
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
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidUserRoleException(request.role());
        }

        if (targetRole != UserRole.ADMIN && targetRole != UserRole.COURIER) {
            throw new InvalidUserRoleException(request.role());
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException(email);
        }

        // Validate vehicle fields for courier
        if (targetRole == UserRole.COURIER) {
            if (request.vehicleType() == null || !ALLOWED_VEHICLE_TYPES.contains(request.vehicleType().trim().toUpperCase())) {
                throw new IllegalArgumentException("Vehicle type must be one of: " + ALLOWED_VEHICLE_TYPES);
            }
            if (request.vehicleNumber() == null || request.vehicleNumber().trim().length() > 20) {
                throw new IllegalArgumentException("Vehicle number must not exceed 20 characters");
            }
        }

        String tempPassword = SecurePasswordGenerator.generateTemporaryPassword(email);

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setRole(targetRole);
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setMustChangePassword(true);

        User savedUser = userRepository.save(user);

        CourierProfile courierProfile = null;
        if (targetRole == UserRole.COURIER) {
            courierProfile = new CourierProfile(
                    savedUser.getId(),
                    request.vehicleType().trim().toUpperCase(),
                    request.vehicleNumber().trim(),
                    "ACTIVE"
            );
            courierProfile = courierProfileRepository.save(courierProfile);
            publishCourierChanged(savedUser, courierProfile);
        }

        auditEvents.record(
                actorId,
                ActorRole.ADMIN,
                "USER_CREATED",
                "USER",
                savedUser.getId().toString(),
                Map.of("role", targetRole.name())
        );

        log.info("Admin {} created user {} with role {}", actorId, savedUser.getId(), targetRole);
        return new CreateUserResponse(toSummaryResponse(savedUser, courierProfile), tempPassword);
    }

    /**
     * Edits permitted user details (name, phone, vehicle info).
     * SRS v2.1 – FR-AUTH-020
     */
    @Transactional
    public AdminUserSummaryResponse updateUser(UUID actorId, UUID userId, EditUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
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
                    publishCourierChanged(updatedUser, courierProfile);
                }
            }
        }

        auditEvents.record(
                actorId,
                ActorRole.ADMIN,
                "USER_UPDATED",
                "USER",
                userId.toString(),
                Map.of("role", user.getRole().name())
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
                publishCourierChanged(savedUser, courierProfile);
            }
        }

        auditEvents.record(actorId, ActorRole.ADMIN, "USER_ACTIVATED", "USER", userId.toString(), Map.of());
        log.info("Admin {} activated user {}", actorId, userId);
        return toSummaryResponse(savedUser, courierProfile);
    }

    /**
     * Deactivates a user account.
     * Enforces self-deactivation guard and cross-service delivery check for couriers.
     * Revokes all active refresh tokens for the deactivated user.
     * SRS v2.1 – Flow 5, FR-AUTH-020
     */
    @Transactional
    public AdminUserSummaryResponse deactivateUser(UUID actorId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Self-deactivation guard (SRS gap: answered 403 ACCESS_DENIED)
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

            // Cross-service delivery check via DeliveryClient
            int activeDeliveries = deliveryClient.getActiveCount(courierIdToCheck);
            if (activeDeliveries > 0) {
                log.warn("Courier {} has {} active deliveries. Deactivation rejected.", courierIdToCheck, activeDeliveries);
                throw new CourierHasActiveDeliveriesException(activeDeliveries);
            }
        }

        user.setStatus(UserStatus.DEACTIVATED);
        User savedUser = userRepository.save(user);

        if (courierProfile != null) {
            courierProfile.setStatus("DEACTIVATED");
            courierProfile = courierProfileRepository.save(courierProfile);
            publishCourierChanged(savedUser, courierProfile);
        }

        // Revoke all active refresh tokens for the deactivated user
        refreshTokenService.revokeAllForUser(userId);

        auditEvents.record(actorId, ActorRole.ADMIN, "USER_DEACTIVATED", "USER", userId.toString(), Map.of());
        log.info("Admin {} deactivated user {}", actorId, userId);
        return toSummaryResponse(savedUser, courierProfile);
    }

    /**
     * Generates a new 16-character temporary password for any local user.
     * Revokes all refresh tokens and sets must_change_password=true.
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
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(userId);

        auditEvents.record(actorId, ActorRole.ADMIN, "USER_PASSWORD_RESET", "USER", userId.toString(), Map.of());
        log.info("Admin {} reset password for user {}", actorId, userId);

        return new ResetPasswordResponse(tempPassword, "Temporary password generated successfully.");
    }

    private void publishCourierChanged(User user, CourierProfile profile) {
        if (user == null || profile == null) return;
        VehicleType vt;
        try {
            vt = VehicleType.valueOf(profile.getVehicleType().trim().toUpperCase());
        } catch (Exception e) {
            vt = VehicleType.MOTORBIKE;
        }
        UserStatus st = UserStatus.valueOf(user.getStatus().name());
        CourierChangedData data = new CourierChangedData(
                user.getId(),
                user.getFullName(),
                st,
                vt,
                profile.getVehicleNumber()
        );
        outboxService.add(EventTypes.COURIER_CHANGED, data);
    }

    public AdminUserSummaryResponse toSummaryResponse(User user) {
        CourierProfile courierProfile = null;
        if (user.getRole() == UserRole.COURIER) {
            courierProfile = courierProfileRepository.findByUserId(user.getId()).orElse(null);
        }
        return toSummaryResponse(user, courierProfile);
    }

    public AdminUserSummaryResponse toSummaryResponse(User user, CourierProfile courierProfile) {
        CourierProfileSummaryResponse courierSummary = null;
        if (courierProfile != null) {
            courierSummary = new CourierProfileSummaryResponse(
                    courierProfile.getId(),
                    courierProfile.getVehicleType(),
                    courierProfile.getVehicleNumber(),
                    courierProfile.getStatus()
            );
        }

        MerchantSummaryResponse merchantSummary = null;
        if (user.getRole() == UserRole.MERCHANT) {
            merchantSummary = merchantRepository.findByUserId(user.getId())
                    .map(m -> new MerchantSummaryResponse(
                            m.getId(),
                            m.getBusinessName(),
                            m.getStatus(),
                            m.getDiscountPercent() != null ? m.getDiscountPercent() : BigDecimal.ZERO
                    ))
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
                courierSummary,
                merchantSummary
        );
    }
}

