package com.swiftdrop.parcel.lifecycle.service;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.Role;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.lifecycle.dto.TransitionCommand;
import com.swiftdrop.parcel.lifecycle.dto.TransitionResult;
import com.swiftdrop.parcel.lifecycle.security.ParcelAccessPolicy;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-PAR-002: ParcelTransitionService is the authoritative code that changes parcels.status.
 * Implements transitions according to Table 2.3, BR-03, BR-04, BR-06, BR-07, BR-16, BR-17, BR-25, BR-28.
 * Validation order: first transition legality (409 INVALID_STATUS_TRANSITION), then input validation (400 VALIDATION_ERROR).
 * Lost optimistic-lock update returns 409 CONCURRENT_UPDATE (FR-PAR-003).
 */
@Service
public class ParcelTransitionService {

    private final ParcelRepository parcelRepository;
    private final ParcelAccessPolicy parcelAccessPolicy;
    private final OutboxService outboxService;
    private final AuditEvents auditEvents;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ParcelTransitionService(
            ParcelRepository parcelRepository,
            ParcelAccessPolicy parcelAccessPolicy,
            OutboxService outboxService,
            AuditEvents auditEvents) {
        this(parcelRepository, parcelAccessPolicy, outboxService, auditEvents, Clock.systemUTC());
    }

    public ParcelTransitionService(
            ParcelRepository parcelRepository,
            ParcelAccessPolicy parcelAccessPolicy,
            OutboxService outboxService,
            AuditEvents auditEvents,
            Clock clock) {
        this.parcelRepository = Objects.requireNonNull(parcelRepository, "parcelRepository");
        this.parcelAccessPolicy = Objects.requireNonNull(parcelAccessPolicy, "parcelAccessPolicy");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executes a parcel status transition in one transaction.
     *
     * @param command the transition command
     * @return the transition result
     */
    @Transactional
    public TransitionResult transition(TransitionCommand command) {
        Objects.requireNonNull(command, "command");

        Parcel parcel = parcelRepository.findById(command.parcelId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));

        ParcelStatus previousStatus = parcel.getStatus();
        ParcelStatus toStatus = command.toStatus();
        ActorRole actorRole = command.actorRole();

        // 1. Transition legality check (Table 2.3, BR-03, BR-06) -> 409 INVALID_STATUS_TRANSITION
        if (!isAllowedTransition(previousStatus, toStatus, actorRole)) {
            throw new DomainException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "This parcel cannot move from " + previousStatus + " to " + toStatus + ".");
        }

        // 2. Input validation check (BR-07, BR-16, BR-17) -> 400 VALIDATION_ERROR
        validateInputs(previousStatus, toStatus, actorRole, command);

        Instant statusChangedAt = clock.instant();

        // Increment sequence and update status
        int newSequence = parcel.getStatusSequence() + 1;
        parcel.setStatusSequence(newSequence);
        parcel.setStatus(toStatus);

        // Terminal at timestamp set only once (BR-06)
        if (isTerminal(toStatus) && parcel.getTerminalAt() == null) {
            parcel.setTerminalAt(statusChangedAt);
        }

        // Save parcel with optimistic locking check (FR-PAR-003)
        try {
            parcelRepository.saveAndFlush(parcel);
        } catch (Exception e) {
            if (isOptimisticLockingFailure(e)) {
                throw new DomainException(ErrorCode.CONCURRENT_UPDATE,
                        "The parcel was updated concurrently by another request. Please re-read and retry.");
            }
            throw e;
        }

        // Outbox event: parcel.status.changed (SRS 3.5, FR-PAR-018, BR-28: staff note travels only here)
        OwnerType ownerType = parcel.getCustomerId() != null ? OwnerType.CUSTOMER : OwnerType.MERCHANT;
        outboxService.add(EventTypes.PARCEL_STATUS_CHANGED, new ParcelStatusChangedData(
                parcel.getId(),
                parcel.getTrackingNumber(),
                newSequence,
                previousStatus,
                toStatus,
                statusChangedAt,
                command.actorUserId(),
                actorRole,
                command.note(),
                command.failureReason(),
                ownerType,
                parcel.getCustomerId(),
                parcel.getMerchantId(),
                parcel.getMerchantReference()));

        return new TransitionResult(
                parcel.getId(),
                parcel.getTrackingNumber(),
                parcel.getStatus(),
                parcel.getStatusSequence(),
                statusChangedAt);
    }

    /**
     * Cancels a parcel as its owner (CUSTOMER or MERCHANT).
     * Enforces CREATED status and accessibility via {@link ParcelAccessPolicy}.
     * Reason is optional (up to 300 chars).
     */
    @Transactional
    public TransitionResult cancelAsOwner(UUID parcelId, CurrentUser user, String reason) {
        if (user == null) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        if (user.role() != Role.CUSTOMER && user.role() != Role.MERCHANT) {
            throw new DomainException(ErrorCode.ACCESS_DENIED, "Access denied");
        }

        // Access policy verifies existence and ownership (throws 404 NOT_FOUND on mismatch, BR-25)
        parcelAccessPolicy.requireAccessible(parcelId, user);

        ActorRole actorRole = ActorRole.valueOf(user.role().name());
        return transition(new TransitionCommand(
                parcelId,
                ParcelStatus.CANCELLED,
                user.userId(),
                actorRole,
                reason,
                null,
                null));
    }

    /**
     * Cancels a parcel as an ADMIN.
     * Enforces CREATED status. Reason is mandatory (10-300 chars).
     * Writes an audit event (action PARCEL_CANCELLED_BY_ADMIN, details: previous status only).
     */
    @Transactional
    public TransitionResult cancelAsAdmin(UUID parcelId, CurrentUser admin, String reason) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new DomainException(ErrorCode.ACCESS_DENIED, "Access denied");
        }

        // Reason validation for admin cancel: required 10-300 characters
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 300) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Validation failed",
                    List.of(new FieldError("reason", "Reason must be between 10 and 300 characters")));
        }

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));
        ParcelStatus previousStatus = parcel.getStatus();

        TransitionResult result = transition(new TransitionCommand(
                parcelId,
                ParcelStatus.CANCELLED,
                admin.userId(),
                ActorRole.ADMIN,
                reason,
                null,
                null));

        // Audit event (BR-28: details hold status only, no note/reason text, no contact data)
        auditEvents.record(
                admin.userId(),
                ActorRole.ADMIN,
                "PARCEL_CANCELLED_BY_ADMIN",
                "PARCEL",
                parcelId.toString(),
                Map.of("previousStatus", previousStatus.name()));

        return result;
    }

    /**
     * Force-fails a parcel stuck after pickup as an ADMIN (FR-PAR-012).
     * Allowed only from PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY.
     * Note is mandatory (5-500 chars). Reason must be allowed for admin override (PARCEL_DAMAGED, OTHER).
     * Writes an audit event (action PARCEL_FAILED_BY_ADMIN, details: previous status and reason code only).
     */
    @Transactional
    public TransitionResult failAsAdmin(UUID parcelId, CurrentUser admin, FailureReason reason, String note) {
        if (admin == null || admin.role() != Role.ADMIN) {
            throw new DomainException(ErrorCode.ACCESS_DENIED, "Access denied");
        }

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));
        ParcelStatus previousStatus = parcel.getStatus();

        TransitionResult result = transition(new TransitionCommand(
                parcelId,
                ParcelStatus.FAILED,
                admin.userId(),
                ActorRole.ADMIN,
                note,
                reason,
                null));

        // Audit event (BR-28: previous status and reason code only, no note, no contact data)
        auditEvents.record(
                admin.userId(),
                ActorRole.ADMIN,
                "PARCEL_FAILED_BY_ADMIN",
                "PARCEL",
                parcelId.toString(),
                Map.of("previousStatus", previousStatus.name(), "failureReason", reason.name()));

        return result;
    }

    /** Table 2.3: Parcel status transitions. */
    private boolean isAllowedTransition(ParcelStatus from, ParcelStatus to, ActorRole actor) {
        if (from == null || to == null || actor == null) {
            return false;
        }
        if (isTerminal(from)) {
            return false;
        }
        return switch (from) {
            case CREATED -> switch (to) {
                case PICKED_UP -> actor == ActorRole.COURIER;
                case CANCELLED -> actor == ActorRole.CUSTOMER || actor == ActorRole.MERCHANT || actor == ActorRole.ADMIN;
                case FAILED -> actor == ActorRole.COURIER;
                default -> false;
            };
            case PICKED_UP -> switch (to) {
                case IN_TRANSIT -> actor == ActorRole.COURIER;
                case FAILED -> actor == ActorRole.COURIER || actor == ActorRole.ADMIN;
                default -> false;
            };
            case IN_TRANSIT -> switch (to) {
                case OUT_FOR_DELIVERY -> actor == ActorRole.COURIER;
                case FAILED -> actor == ActorRole.COURIER || actor == ActorRole.ADMIN;
                default -> false;
            };
            case OUT_FOR_DELIVERY -> switch (to) {
                case DELIVERED -> actor == ActorRole.COURIER;
                case FAILED -> actor == ActorRole.COURIER || actor == ActorRole.ADMIN;
                default -> false;
            };
            default -> false;
        };
    }

    private boolean isTerminal(ParcelStatus status) {
        return status == ParcelStatus.DELIVERED
                || status == ParcelStatus.FAILED
                || status == ParcelStatus.CANCELLED;
    }

    private void validateInputs(
            ParcelStatus fromStatus,
            ParcelStatus toStatus,
            ActorRole actorRole,
            TransitionCommand command) {
        List<FieldError> errors = new ArrayList<>();

        // General note length limit up to 500
        if (command.note() != null && command.note().length() > 500) {
            errors.add(new FieldError("note", "Note must not exceed 500 characters"));
        }

        if (toStatus == ParcelStatus.DELIVERED) {
            // BR-17: recipientName required, 2-100 characters
            String recipient = command.recipientName();
            if (recipient == null || recipient.trim().length() < 2 || recipient.trim().length() > 100) {
                errors.add(new FieldError("recipientName", "Recipient name must be between 2 and 100 characters"));
            }
        } else if (toStatus == ParcelStatus.FAILED) {
            // BR-16, Table 2.4: failure reason must be allowed for stage
            FailureReason reason = command.failureReason();
            if (reason == null) {
                errors.add(new FieldError("failureReason", "Failure reason is required"));
            } else {
                boolean adminOverride = (actorRole == ActorRole.ADMIN);
                Set<FailureReason> allowed = FailureReason.allowedFor(fromStatus, adminOverride);
                if (!allowed.contains(reason)) {
                    errors.add(new FieldError("failureReason", "Failure reason is not allowed for status " + fromStatus));
                }

                // OTHER requires a note of 5-500 characters
                if (reason == FailureReason.OTHER) {
                    String note = command.note();
                    if (note == null || note.trim().length() < 5 || note.trim().length() > 500) {
                        errors.add(new FieldError("note", "Note must be between 5 and 500 characters when reason is OTHER"));
                    }
                }
            }

            // Admin override always requires a note of 5-500 characters
            if (actorRole == ActorRole.ADMIN) {
                String note = command.note();
                if (note == null || note.trim().length() < 5 || note.trim().length() > 500) {
                    errors.add(new FieldError("note", "Note must be between 5 and 500 characters for admin override"));
                }
            }
        } else if (toStatus == ParcelStatus.CANCELLED) {
            String reason = command.note();
            if (actorRole == ActorRole.ADMIN) {
                // BR-07: Admin cancel requires 10-300 characters
                if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 300) {
                    errors.add(new FieldError("reason", "Reason must be between 10 and 300 characters"));
                }
            } else {
                // BR-07: Owner cancel optional up to 300 characters
                if (reason != null && reason.length() > 300) {
                    errors.add(new FieldError("reason", "Reason must not exceed 300 characters"));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Validation failed", errors);
        }
    }

    private boolean isOptimisticLockingFailure(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof OptimisticLockException
                    || current instanceof StaleObjectStateException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

