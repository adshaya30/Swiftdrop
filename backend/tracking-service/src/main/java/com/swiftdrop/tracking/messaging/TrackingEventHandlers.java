package com.swiftdrop.tracking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.ProcessedEvents;
import com.swiftdrop.common.events.RetryableEventException;
import com.swiftdrop.common.events.data.*;
import com.swiftdrop.tracking.entity.AuditLog;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.entity.TrackingParcel;
import com.swiftdrop.tracking.repository.AuditLogRepository;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import com.swiftdrop.tracking.repository.TrackingParcelRepository;
import com.swiftdrop.tracking.service.SseEmitterService;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional event handlers for the tracking service (FR-TRK-001).
 * Each handler runs inside the retry loop of {@link com.swiftdrop.common.events.EventConsumerSupport}.
 */
@Component
public class TrackingEventHandlers {

    private static final Logger log = LoggerFactory.getLogger(TrackingEventHandlers.class);

    private final TrackingParcelRepository parcelRepo;
    private final TrackingEventRepository eventRepo;
    private final AuditLogRepository auditRepo;
    private final ProcessedEvents processedEvents;
    private final SseEmitterService sseEmitterService;
    private final ObjectMapper objectMapper;

    public TrackingEventHandlers(TrackingParcelRepository parcelRepo,
                                 TrackingEventRepository eventRepo,
                                 AuditLogRepository auditRepo,
                                 ProcessedEvents processedEvents,
                                 SseEmitterService sseEmitterService,
                                 ObjectMapper objectMapper) {
        this.parcelRepo = parcelRepo;
        this.eventRepo = eventRepo;
        this.auditRepo = auditRepo;
        this.processedEvents = processedEvents;
        this.sseEmitterService = sseEmitterService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(EventEnvelope<?> envelope) {
        // De-duplicate via processed_events (SRS 3.5) - except audit.recorded uses its own unique key
        if (!EventTypes.AUDIT_RECORDED.equals(envelope.eventType())) {
            if (!processedEvents.firstTime(envelope.eventId())) {
                log.debug("Duplicate event {} ignored", envelope.eventId());
                return;
            }
        }

        switch (envelope.eventType()) {
            case EventTypes.PARCEL_CREATED -> handleParcelCreated(envelope);
            case EventTypes.PARCEL_STATUS_CHANGED -> handleStatusChanged(envelope);
            case EventTypes.DELIVERY_ASSIGNED -> handleDeliveryAssigned(envelope);
            case EventTypes.DELIVERY_UNASSIGNED -> handleDeliveryUnassigned(envelope);
            case EventTypes.AUDIT_RECORDED -> handleAuditRecorded(envelope);
            default -> log.warn("Unexpected event type: {}", envelope.eventType());
        }
    }

    /**
     * parcel.created → create tracking_parcels + first timeline row (FR-TRK-001).
     */
    private void handleParcelCreated(EventEnvelope<?> envelope) {
        var data = (ParcelCreatedData) envelope.data();

        TrackingParcel parcel = new TrackingParcel();
        parcel.setParcelId(data.parcelId());
        parcel.setTrackingNumber(data.trackingNumber());
        parcel.setStatus(data.status().name());
        parcel.setStatusSequence(1);
        parcel.setOwnerType(data.ownerType().name());
        parcel.setCustomerId(data.customerId());
        parcel.setMerchantId(data.merchantId());
        parcel.setOriginDistrictName(data.originDistrictName());
        parcel.setOriginZoneName(data.originZoneName());
        parcel.setDestinationDistrictName(data.destinationDistrictName());
        parcel.setDestinationZoneName(data.destinationZoneName());
        parcel.setCreatedAt(data.createdAt());
        parcel.setUpdatedAt(data.createdAt());
        parcelRepo.save(parcel);

        // First timeline row: sequence 1, from_status null, to_status CREATED
        TrackingEvent event = new TrackingEvent();
        event.setParcelId(data.parcelId());
        event.setSequence(1);
        event.setFromStatus(null);
        event.setToStatus(data.status().name());
        event.setActorRole(data.ownerType().name());
        event.setActorUserId(data.customerId());
        event.setOccurredAt(data.createdAt());
        eventRepo.save(event);

        log.info("Created tracking for parcel {}", data.trackingNumber());
    }

    /**
     * parcel.status.changed (FR-TRK-001): unknown parcel = retryable; stale sequence = ack and ignore.
     * Push to SSE emitters only after the transaction commits.
     */
    private void handleStatusChanged(EventEnvelope<?> envelope) {
        var data = (ParcelStatusChangedData) envelope.data();

        TrackingParcel parcel = parcelRepo.findById(data.parcelId())
                .orElseThrow(() -> new RetryableEventException(
                        "Unknown parcel " + data.parcelId() + "; will retry"));

        // Stale sequence: acknowledge and ignore
        if (data.sequence() <= parcel.getStatusSequence()) {
            log.debug("Stale sequence {} for parcel {}, current {}", data.sequence(),
                    data.parcelId(), parcel.getStatusSequence());
            return;
        }

        // Append tracking event
        TrackingEvent event = new TrackingEvent();
        event.setParcelId(data.parcelId());
        event.setSequence(data.sequence());
        event.setFromStatus(data.previousStatus() != null ? data.previousStatus().name() : null);
        event.setToStatus(data.status().name());
        event.setActorUserId(data.actorUserId());
        event.setActorRole(data.actorRole() != null ? data.actorRole().name() : "SYSTEM");
        event.setFailureReasonCode(data.failureReasonCode() != null ? data.failureReasonCode().name() : null);
        event.setNote(data.note());
        event.setOccurredAt(data.statusChangedAt());
        eventRepo.save(event);

        // Update parcel projection
        parcel.setStatus(data.status().name());
        parcel.setStatusSequence(data.sequence());
        parcel.setUpdatedAt(Instant.now());
        parcelRepo.save(parcel);

        // Push to SSE emitters AFTER transaction commits (FR-TRK-003)
        String trackingNumber = parcel.getTrackingNumber();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterService.sendStatusUpdate(trackingNumber, parcel);
            }
        });

        log.info("Status changed for {} to {}", data.trackingNumber(), data.status());
    }

    /**
     * delivery.assigned → set courier_id and courier_first_name.
     */
    private void handleDeliveryAssigned(EventEnvelope<?> envelope) {
        var data = (DeliveryAssignedData) envelope.data();
        TrackingParcel parcel = parcelRepo.findById(data.parcelId())
                .orElseThrow(() -> new RetryableEventException(
                        "Unknown parcel " + data.parcelId() + " for delivery.assigned"));
        parcel.setCourierId(data.courierId());
        parcel.setCourierFirstName(data.courierFirstName());
        parcel.setUpdatedAt(Instant.now());
        parcelRepo.save(parcel);
        log.info("Courier assigned to parcel {}", data.parcelId());
    }

    /**
     * delivery.unassigned → clear courier_id and courier_first_name.
     */
    private void handleDeliveryUnassigned(EventEnvelope<?> envelope) {
        var data = (DeliveryUnassignedData) envelope.data();
        TrackingParcel parcel = parcelRepo.findById(data.parcelId())
                .orElseThrow(() -> new RetryableEventException(
                        "Unknown parcel " + data.parcelId() + " for delivery.unassigned"));
        parcel.setCourierId(null);
        parcel.setCourierFirstName(null);
        parcel.setUpdatedAt(Instant.now());
        parcelRepo.save(parcel);
        log.info("Courier unassigned from parcel {}", data.parcelId());
    }

    /**
     * audit.recorded → append audit_logs (unique event_id, idempotent).
     */
    private void handleAuditRecorded(EventEnvelope<?> envelope) {
        var data = (AuditRecordedData) envelope.data();

        // Idempotency by unique event_id
        if (auditRepo.existsByEventId(envelope.eventId())) {
            log.debug("Duplicate audit event {} ignored", envelope.eventId());
            return;
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setEventId(envelope.eventId());
        auditLog.setService(data.service());
        auditLog.setActorUserId(data.actorUserId());
        auditLog.setActorRole(data.actorRole() != null ? data.actorRole().name() : "SYSTEM");
        auditLog.setAction(data.action());
        auditLog.setEntityType(data.entityType());
        auditLog.setEntityId(data.entityId());
        auditLog.setDetails(data.details() != null ? objectMapper.valueToTree(data.details()) : null);
        auditLog.setOccurredAt(data.occurredAt());
        auditRepo.save(auditLog);

        log.info("Audit log recorded: {} on {}/{}", data.action(), data.entityType(), data.entityId());
    }
}

