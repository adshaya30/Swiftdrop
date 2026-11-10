package com.swiftdrop.common.events;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.events.data.AuditRecordedData;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.security.CurrentUsers;
import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Writes {@code audit.recorded} through the outbox in the caller's transaction (FR-AUTH-023, FR-PRC-008, FR-DEL-002,
 * FR-PAR-011, FR-PAR-012, NFR-SEC-019). Details never contain passwords, secrets, tokens or contact data (BR-28):
 * forbidden keys are rejected with an {@link IllegalArgumentException} so mistakes surface in tests.
 */
public class AuditEvents {

    /** Key words that are never allowed as (part of) a detail key. */
    static final Set<String> FORBIDDEN_WORDS =
            Set.of("password", "secret", "token", "email", "phone", "address", "credential", "credentials");
    /** "name" is forbidden alone and after these words (person names); "zoneName" and similar are allowed. */
    static final Set<String> PERSON_NAME_PREFIXES =
            Set.of("full", "first", "last", "user", "contact", "recipient", "sender", "customer");

    private final OutboxService outbox;
    private final String serviceName;
    private final Clock clock;

    public AuditEvents(OutboxService outbox, String serviceName, Clock clock) {
        this.outbox = outbox;
        this.serviceName = serviceName;
        this.clock = clock;
    }

    /** Audit an action of the authenticated caller ({@link CurrentUsers#get()}). */
    public void record(String action, String entityType, String entityId, Map<String, Object> details) {
        CurrentUser user = CurrentUsers.get();
        record(user.userId(), ActorRole.valueOf(user.role().name()), action, entityType, entityId, details);
    }

    /** Audit an action of an explicit actor; {@code actorUserId} is null for {@link ActorRole#SYSTEM}. */
    public void record(UUID actorUserId, ActorRole actorRole, String action, String entityType, String entityId,
                       Map<String, Object> details) {
        Objects.requireNonNull(actorRole, "actorRole");
        requireText(action, "action");
        requireText(entityType, "entityType");
        requireText(entityId, "entityId");
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalStateException("spring.application.name must be set to record audit events");
        }
        Map<String, Object> safe = details == null ? Map.of() : details;
        checkKeys(safe);
        outbox.add(EventTypes.AUDIT_RECORDED, new AuditRecordedData(serviceName, actorUserId, actorRole, action,
                entityType, entityId, Collections.unmodifiableMap(new LinkedHashMap<>(safe)), clock.instant()));
    }

    static void checkKeys(Map<?, ?> details) {
        for (Map.Entry<?, ?> entry : details.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (isForbidden(key)) {
                throw new IllegalArgumentException("Audit detail key '" + key
                        + "' is not allowed: no passwords, secrets, tokens or contact data (BR-28)");
            }
            checkValue(entry.getValue());
        }
    }

    private static void checkValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            checkKeys(nested);
        } else if (value instanceof Collection<?> items) {
            items.forEach(AuditEvents::checkValue);
        }
    }

    static boolean isForbidden(String key) {
        String[] words = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT)
                .split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (FORBIDDEN_WORDS.contains(w) || w.equals("username")) {
                return true;
            }
            if (w.equals("name") && (words.length == 1 || (i > 0 && PERSON_NAME_PREFIXES.contains(words[i - 1])))) {
                return true;
            }
        }
        return false;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
