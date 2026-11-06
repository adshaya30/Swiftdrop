package com.swiftdrop.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.entity.CourierProfile;
import com.swiftdrop.auth.entity.OutboxEvent;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing transactional audit and domain events written to outbox_events.
 * SRS v2.1 – Section 3.5, FR-AUTH-023
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records an audit event in the same transaction as the business operation.
     * Guaranteed never to include passwords, hashes, refresh tokens or private keys.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAuditEvent(String action, UUID actorId, UUID targetUserId, String targetRole, Map<String, Object> details) {
        try {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();

            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("eventId", eventId.toString());
            eventPayload.put("action", action);
            eventPayload.put("actorId", actorId != null ? actorId.toString() : null);
            eventPayload.put("targetUserId", targetUserId.toString());
            eventPayload.put("targetRole", targetRole);
            eventPayload.put("details", details != null ? sanitizeDetails(details) : Map.of());
            eventPayload.put("timestamp", now.toString());

            String json = objectMapper.writeValueAsString(eventPayload);
            OutboxEvent outboxEvent = new OutboxEvent("USER", targetUserId, "audit.recorded", json);
            outboxEventRepository.save(outboxEvent);

            log.debug("Recorded audit event '{}' for user {}", action, targetUserId);
        } catch (Exception e) {
            log.error("Failed to serialize audit event for action {}: {}", action, e.getMessage(), e);
            throw new RuntimeException("Could not persist audit event", e);
        }
    }

    /**
     * Records a courier.changed event when a courier is created, updated, activated, or deactivated.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCourierChangedEvent(User user, CourierProfile profile) {
        try {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();

            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("eventId", eventId.toString());
            eventPayload.put("courierId", profile.getId().toString());
            eventPayload.put("userId", user.getId().toString());
            eventPayload.put("fullName", user.getFullName());
            eventPayload.put("phone", user.getPhone());
            eventPayload.put("vehicleType", profile.getVehicleType());
            eventPayload.put("vehicleNumber", profile.getVehicleNumber());
            eventPayload.put("status", profile.getStatus());
            eventPayload.put("timestamp", now.toString());

            String json = objectMapper.writeValueAsString(eventPayload);
            OutboxEvent outboxEvent = new OutboxEvent("COURIER", profile.getId(), "courier.changed", json);
            outboxEventRepository.save(outboxEvent);

            log.debug("Recorded courier.changed event for courierId={}", profile.getId());
        } catch (Exception e) {
            log.error("Failed to serialize courier.changed event for user {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Could not persist courier event", e);
        }
    }

    private Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("password") || key.contains("secret") || key.contains("token") || key.contains("hash")) {
                continue; // strip sensitive fields
            }
            sanitized.put(entry.getKey(), entry.getValue());
        }
        return sanitized;
    }
}

