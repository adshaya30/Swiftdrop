package com.swiftdrop.parcel.create.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.parcel.create.dto.CreationResult;
import com.swiftdrop.parcel.create.scheduler.IdempotencyCleanupScheduler;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static com.swiftdrop.parcel.support.ParcelFixtures.parcel;
import static org.assertj.core.api.Assertions.*;

@Tag("TST-021")
class IdempotencyDatabaseTests extends AbstractParcelDatabaseTest {
    @Autowired IdempotencyService service;
    @Autowired IdempotencyCleanupScheduler scheduler;
    @Autowired ParcelRepository parcels;
    @Autowired TrackingNumberGenerator tracking;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private final UUID owner = UUID.randomUUID();
    private UUID parcelId;
    private String hash;
    private TransactionTemplate transaction;

    @BeforeEach
    void prepare() {
        transaction = new TransactionTemplate(manager);
        parcelId = parcels.saveAndFlush(parcel(tracking.generate(), owner, null, null)).getId();
        hash = service.hashRequest(Map.of("expectedFee", new java.math.BigDecimal("800.00")));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM idempotency_keys WHERE parcel_id IN (SELECT id FROM parcels WHERE customer_id=?)", owner);
        jdbc.update("DELETE FROM parcels WHERE customer_id=?", owner);
    }

    private CreationResult result() {
        return new CreationResult(201, Map.of("id", parcelId.toString(), "total", new java.math.BigDecimal("800.00")),
                false, parcelId);
    }

    private void store(String key) {
        transaction.executeWithoutResult(tx -> service.storeSuccess(OwnerType.CUSTOMER, owner, key, hash, result()));
    }

    @Test
    void replaysOriginalResponseAndCanonicalNumbersWithoutInvokingPricing() {
        assertThat(service.findReplay(OwnerType.CUSTOMER, owner, "key", hash)).isEmpty();
        store("key");
        String equivalent = service.hashRequest(Map.of("expectedFee", 800));
        var replay = service.findReplay(OwnerType.CUSTOMER, owner, "key", equivalent).orElseThrow();
        assertThat(replay.httpStatus()).isEqualTo(201);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.parcelId()).isEqualTo(parcelId);
        assertThat(((JsonNode) replay.body()).get("total").decimalValue()).isEqualByComparingTo("800.00");
        assertThat(keyCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.findReplay(OwnerType.CUSTOMER, owner, "key",
                service.hashRequest(Map.of("expectedFee", 801))))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void scopesTheSameKeyByBothOwnerTypeAndOwnerId() {
        store("same");
        UUID secondOwner = UUID.randomUUID();
        assertThat(service.findReplay(OwnerType.MERCHANT, owner, "same", hash)).isEmpty();
        assertThat(service.findReplay(OwnerType.CUSTOMER, secondOwner, "same", hash)).isEmpty();
        transaction.executeWithoutResult(tx -> {
            service.storeSuccess(OwnerType.MERCHANT, owner, "same", hash, result());
            service.storeSuccess(OwnerType.CUSTOMER, secondOwner, "same", hash, result());
        });
        assertThat(keyCount()).isEqualTo(3);
    }

    @Test
    void expiresOldRowsAndReusesTheKeyBeforeHourlyCleanup() {
        store("expired");
        jdbc.update("UPDATE idempotency_keys SET created_at=? WHERE parcel_id=?",
                Timestamp.from(Instant.now().minusSeconds(90000)), parcelId);
        String changed = service.hashRequest(Map.of("expectedFee", 900));
        assertThat(service.findReplay(OwnerType.CUSTOMER, owner, "expired", changed)).isEmpty();
        transaction.executeWithoutResult(tx ->
                service.storeSuccess(OwnerType.CUSTOMER, owner, "expired", changed, result()));
        assertThat(service.findReplay(OwnerType.CUSTOMER, owner, "expired", changed)).isPresent();
        assertThat(keyCount()).isEqualTo(1);
    }

    @Test
    void hourlyCleanupDeletesExpiredRowsAndRetainsFreshRows() {
        store("old");
        store("fresh");
        jdbc.update("UPDATE idempotency_keys SET created_at=? WHERE parcel_id=? AND idempotency_key='old'",
                Timestamp.from(Instant.now().minusSeconds(90000)), parcelId);
        scheduler.purgeExpired();
        assertThat(service.findReplay(OwnerType.CUSTOMER, owner, "old", hash)).isEmpty();
        assertThat(service.findReplay(OwnerType.CUSTOMER, owner, "fresh", hash)).isPresent();
        assertThat(keyCount()).isEqualTo(1);
    }

    @Test
    void requiresCallerTransactionAndRollsBackKeysOnFailure() {
        assertThatThrownBy(() -> service.storeSuccess(OwnerType.CUSTOMER, owner, "key", hash, result()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
            service.storeSuccess(OwnerType.CUSTOMER, owner, "key", hash, result());
            throw new IllegalStateException("synthetic transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.findReplay(OwnerType.CUSTOMER, owner, "key", hash)).isEmpty();
        assertThat(keyCount()).isZero();
        store("key");
        assertThat(keyCount()).isEqualTo(1);
    }

    @Test
    void failedResponsesStoreNothingAndKeyRemainsReusable() {
        for (int status : new int[]{400, 409, 422, 500, 503}) {
            assertThatThrownBy(() -> transaction.executeWithoutResult(tx ->
                    service.storeSuccess(OwnerType.CUSTOMER, owner, "key", hash,
                            new CreationResult(status, Map.of(), false, parcelId))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(keyCount()).isZero();
        store("key");
        assertThat(keyCount()).isEqualTo(1);
    }

    @Test
    void exactExpiryBoundaryIsNotActive() {
        store("boundary");
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("UPDATE idempotency_keys SET created_at=? WHERE parcel_id=?", Timestamp.from(cutoff), parcelId);
        var repository = new com.swiftdrop.parcel.create.repository.IdempotencyRepository(jdbc);
        assertThat(repository.findActive(OwnerType.CUSTOMER, owner, "boundary", cutoff)).isEmpty();
        assertThat(repository.findActive(OwnerType.CUSTOMER, owner, "boundary", cutoff.minusNanos(1000))).isPresent();
    }

    @Test
    void concurrentDuplicateRollsBackLoserThenReplaysWinnerFromFreshTransaction() throws Exception {
        CyclicBarrier ready = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<CreationResult> attempt = () -> {
                try {
                    return transaction.execute(tx -> {
                        // Test harness simulates caller persistence; production orchestration remains Phase 3.
                        var saved = parcels.saveAndFlush(parcel(tracking.generate(), owner, null, null));
                        var response = new CreationResult(201, Map.of("id", saved.getId().toString()), false, saved.getId());
                        try { ready.await(30, TimeUnit.SECONDS); }
                        catch (Exception e) { throw new IllegalStateException(e); }
                        service.storeSuccess(OwnerType.CUSTOMER, owner, "race", hash, response);
                        return response;
                    });
                } catch (DataIntegrityViolationException e) {
                    // execute has already rolled back the entire losing transaction here.
                    return service.findReplay(OwnerType.CUSTOMER, owner, "race", hash).orElseThrow();
                }
            };
            Future<CreationResult> first = executor.submit(attempt);
            Future<CreationResult> second = executor.submit(attempt);
            CreationResult a = first.get(60, TimeUnit.SECONDS);
            CreationResult b = second.get(60, TimeUnit.SECONDS);
            assertThat(a.parcelId()).isEqualTo(b.parcelId());
            assertThat(a.replay()).isNotEqualTo(b.replay());
            assertThat(keyCount()).isEqualTo(1);
            // One setup parcel plus exactly one committed concurrent parcel; no orphan loser.
            assertThat(jdbc.queryForObject("SELECT count(*) FROM parcels WHERE customer_id=?", Integer.class, owner))
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int keyCount() {
        return jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE parcel_id IN "
                + "(SELECT id FROM parcels WHERE customer_id=?)", Integer.class, owner);
    }
}
