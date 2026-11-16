package com.swiftdrop.parcel.create.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.parcel.create.dto.CreationResult;
import com.swiftdrop.parcel.create.repository.IdempotencyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("TST-021")
class IdempotencyServiceTests {
    private final IdempotencyRepository repository = mock(IdempotencyRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CanonicalRequestHasher hasher = new CanonicalRequestHasher(mapper);
    private final Instant now = Instant.parse("2026-11-10T12:00:00Z");
    private final IdempotencyService service = new IdempotencyService(repository, hasher, mapper,
            Clock.fixed(now, ZoneOffset.UTC));
    private final UUID owner = UUID.randomUUID();
    private final String hash = hasher.hash(Map.of("expectedFee", 800));

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "key space", "a/b", "a.b", "é", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidKeysWithFieldError(String key) {
        assertThatThrownBy(() -> service.findReplay(OwnerType.CUSTOMER, owner, key, hash))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.errors()).extracting("field").containsExactly("Idempotency-Key");
                });
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsOptionalAndBoundaryKeys() {
        service.validateKey(null);
        service.validateKey("a");
        service.validateKey("A".repeat(64));
        service.validateKey("A_z-09");
        assertThat(service.findReplay(OwnerType.CUSTOMER, owner, null, null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void hashesCompleteValidatedBodyAndQueriesExact24HourBoundary() {
        assertThat(service.hashRequest(Map.of("expectedFee", 800))).isEqualTo(hash);
        when(repository.findActive(OwnerType.CUSTOMER, owner, "key", now.minusSeconds(86400)))
                .thenReturn(Optional.empty());
        assertThat(service.findReplay(OwnerType.CUSTOMER, owner, "key", hash)).isEmpty();
        verify(repository).findActive(OwnerType.CUSTOMER, owner, "key", now.minusSeconds(86400));
        service.purgeExpired();
        verify(repository).deleteExpired(now.minusSeconds(86400));
    }

    @Test
    void replaysOriginalStatusBodyAndParcelId() {
        UUID id = UUID.randomUUID();
        when(repository.findActive(any(), any(), any(), any())).thenReturn(Optional.of(
                new IdempotencyRepository.StoredResponse(hash, 201, "{\"total\":800.01}", id)));
        var result = service.findReplay(OwnerType.CUSTOMER, owner, "key", hash).orElseThrow();
        assertThat(result.replay()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.parcelId()).isEqualTo(id);
        assertThat(((com.fasterxml.jackson.databind.JsonNode) result.body()).get("total").decimalValue())
                .isEqualByComparingTo("800.01");
    }

    @Test
    void conflictingHashReturns422WithoutWriting() {
        when(repository.findActive(any(), any(), any(), any())).thenReturn(Optional.of(
                new IdempotencyRepository.StoredResponse("f".repeat(64), 201, "{}", UUID.randomUUID())));
        assertThatThrownBy(() -> service.findReplay(OwnerType.CUSTOMER, owner, "key", hash))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 422, 500, 503})
    void refusesToPersistFailedResponses(int status) {
        assertThatThrownBy(() -> service.storeSuccess(OwnerType.CUSTOMER, owner, "key", hash,
                new CreationResult(status, Map.of(), false, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void neverStoresReplayOrOptionalKeyAndRejectsInvalidHashes() {
        service.storeSuccess(OwnerType.CUSTOMER, owner, null, null, null);
        assertThatThrownBy(() -> service.storeSuccess(OwnerType.CUSTOMER, owner, "key", hash,
                new CreationResult(201, Map.of(), true, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findReplay(OwnerType.CUSTOMER, owner, "key", "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
