package com.swiftdrop.parcel.create.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.parcel.create.dto.CreationResult;
import com.swiftdrop.parcel.create.repository.IdempotencyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-PAR-006 shared implementation. Phase 3 must call findReplay BEFORE pricing.
 * storeSuccess joins the parcel/outbox transaction; it never commits a key independently.
 * A uniqueness loser must let that transaction roll back, then call findReplay from
 * outside the failed transaction. REQUIRES_NEW gives the reread a fresh committed snapshot.
 * Do not catch an insert failure and continue using a rollback-only PostgreSQL transaction.
 */
@Service
public class IdempotencyService {
    private static final Duration LIFETIME = Duration.ofHours(24);
    private final IdempotencyRepository repository;
    private final CanonicalRequestHasher hasher;
    private final ObjectMapper mapper;
    private final ObjectReader replayReader;
    private final Clock clock;

    @Autowired
    public IdempotencyService(IdempotencyRepository repository, CanonicalRequestHasher hasher, ObjectMapper mapper) {
        this(repository, hasher, mapper, Clock.systemUTC());
    }

    IdempotencyService(IdempotencyRepository repository, CanonicalRequestHasher hasher,
                       ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.hasher = hasher;
        this.mapper = mapper;
        // Preserve monetary decimal scale in the original response (including 0.00).
        this.replayReader = mapper.copy().setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
                .readerFor(JsonNode.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.clock = clock;
    }

    /** Hash the complete validated customer/merchant body, including expectedFee and merchantReference. */
    public String hashRequest(Object validatedBody) {
        return hasher.hash(validatedBody);
    }

    public void validateKey(String key) {
        if (key != null && !key.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Invalid idempotency key",
                    List.of(new FieldError("Idempotency-Key", "must contain 1-64 letters, digits, underscores or hyphens")));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<CreationResult> findReplay(OwnerType ownerType, UUID ownerId, String key, String requestHash) {
        validateKey(key);
        if (key == null) {
            return Optional.empty();
        }
        validateScopeAndHash(ownerType, ownerId, requestHash);
        return repository.findActive(ownerType, ownerId, key, clock.instant().minus(LIFETIME)).map(stored -> {
            if (!stored.requestHash().equals(requestHash)) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "The idempotency key was already used for a different request");
            }
            try {
                JsonNode body = replayReader.readValue(stored.body());
                return new CreationResult(stored.status(), body, true, stored.parcelId());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Stored response could not be read", e);
            }
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void storeSuccess(OwnerType ownerType, UUID ownerId, String key,
                             String requestHash, CreationResult result) {
        validateKey(key);
        if (key == null) {
            return;
        }
        validateScopeAndHash(ownerType, ownerId, requestHash);
        Objects.requireNonNull(result);
        if (result.httpStatus() < 200 || result.httpStatus() >= 300 || result.replay()) {
            throw new IllegalArgumentException("Only a new successful creation response can be stored");
        }
        Objects.requireNonNull(result.parcelId());
        Objects.requireNonNull(result.body());
        try {
            Instant now = clock.instant();
            repository.insertSuccess(ownerType, ownerId, key, requestHash, result.httpStatus(),
                    mapper.writeValueAsString(result.body()), result.parcelId(), now, now.minus(LIFETIME));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Creation response could not be stored", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeExpired() {
        return repository.deleteExpired(clock.instant().minus(LIFETIME));
    }

    private static void validateScopeAndHash(OwnerType ownerType, UUID ownerId, String hash) {
        Objects.requireNonNull(ownerType);
        Objects.requireNonNull(ownerId);
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A canonical SHA-256 request hash is required");
        }
    }
}
