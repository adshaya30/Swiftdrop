package com.swiftdrop.tracking.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** FR-TRK-005: one audit entry; {@code details} is a JSON object without secrets or contact data (BR-28). */
public record AuditLogResponse(Long id, String service, UUID actorUserId, String actorRole, String action,
                               String entityType, String entityId, JsonNode details, Instant occurredAt) { }
