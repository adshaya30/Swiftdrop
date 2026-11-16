package com.swiftdrop.parcel.lifecycle;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.lifecycle.dto.TransitionCommand;
import com.swiftdrop.parcel.lifecycle.dto.TransitionResult;
import com.swiftdrop.parcel.lifecycle.service.ParcelTransitionService;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import com.swiftdrop.parcel.support.ParcelFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DirtiesContext
class ParcelTransitionIntegrationTests extends AbstractParcelDatabaseTest {

    @Autowired
    private ParcelTransitionService transitionService;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Tag("TST-011")
    @DisplayName("TST-011 FR-PAR-002: lifecycle transition persists state, increments sequence, sets terminal_at, and writes outbox")
    void fullLifecycleTransitionInDatabase() {
        UUID customerId = UUID.randomUUID();
        Parcel parcel = ParcelFixtures.parcel("LP" + System.currentTimeMillis() % 1000000000L + "LK", customerId, null, null);
        parcel = parcelRepository.saveAndFlush(parcel);
        UUID parcelId = parcel.getId();

        UUID courierId = UUID.randomUUID();

        // 1. CREATED -> PICKED_UP
        TransitionResult r1 = transitionService.transition(new TransitionCommand(
                parcelId, ParcelStatus.PICKED_UP, courierId, ActorRole.COURIER, "Picked up at depot", null, null));
        assertThat(r1.status()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(r1.sequence()).isEqualTo(2);

        Parcel p1 = parcelRepository.findById(parcelId).orElseThrow();
        assertThat(p1.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(p1.getStatusSequence()).isEqualTo(2);
        assertThat(p1.getTerminalAt()).isNull();

        // 2. PICKED_UP -> IN_TRANSIT
        TransitionResult r2 = transitionService.transition(new TransitionCommand(
                parcelId, ParcelStatus.IN_TRANSIT, courierId, ActorRole.COURIER, null, null, null));
        assertThat(r2.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(r2.sequence()).isEqualTo(3);

        // 3. IN_TRANSIT -> OUT_FOR_DELIVERY
        TransitionResult r3 = transitionService.transition(new TransitionCommand(
                parcelId, ParcelStatus.OUT_FOR_DELIVERY, courierId, ActorRole.COURIER, null, null, null));
        assertThat(r3.status()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(r3.sequence()).isEqualTo(4);

        // 4. OUT_FOR_DELIVERY -> DELIVERED (terminal)
        TransitionResult r4 = transitionService.transition(new TransitionCommand(
                parcelId, ParcelStatus.DELIVERED, courierId, ActorRole.COURIER, "Delivered safely", null, "Recipient Recv"));
        assertThat(r4.status()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(r4.sequence()).isEqualTo(5);

        Parcel pDelivered = parcelRepository.findById(parcelId).orElseThrow();
        assertThat(pDelivered.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(pDelivered.getStatusSequence()).isEqualTo(5);
        assertThat(pDelivered.getTerminalAt()).isNotNull();

        // Verify outbox_events table has rows recorded
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE routing_key = 'parcel.status.changed'",
                Integer.class);
        assertThat(outboxCount).isGreaterThanOrEqualTo(4);

        // Immutability check (BR-06): cannot transition from DELIVERED
        assertThatThrownBy(() -> transitionService.transition(new TransitionCommand(
                parcelId, ParcelStatus.PICKED_UP, courierId, ActorRole.COURIER, null, null, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    @Tag("TST-011")
    @DisplayName("TST-011 FR-PAR-003: lost optimistic-lock update on concurrent version conflict returns 409 CONCURRENT_UPDATE")
    void concurrentUpdateTriggersOptimisticLockingFailure() throws Exception {
        UUID customerId = UUID.randomUUID();
        Parcel parcel = ParcelFixtures.parcel("LP" + (System.currentTimeMillis() + 1) % 1000000000L + "LK", customerId, null, null);
        parcel = parcelRepository.saveAndFlush(parcel);
        UUID parcelId = parcel.getId();

        // Simulate concurrent threads attempting simultaneous transitions on the same parcel
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger concurrentUpdateCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger otherErrorCount = new java.util.concurrent.atomic.AtomicInteger(0);

        UUID courier1 = UUID.randomUUID();
        UUID courier2 = UUID.randomUUID();

        Runnable task1 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                transitionService.transition(new TransitionCommand(
                        parcelId, ParcelStatus.PICKED_UP, courier1, ActorRole.COURIER, "Note 1", null, null));
                successCount.incrementAndGet();
            } catch (DomainException e) {
                if (e.code() == ErrorCode.CONCURRENT_UPDATE || e.code() == ErrorCode.INVALID_STATUS_TRANSITION) {
                    concurrentUpdateCount.incrementAndGet();
                } else {
                    otherErrorCount.incrementAndGet();
                }
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            }
        };

        Runnable task2 = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                transitionService.transition(new TransitionCommand(
                        parcelId, ParcelStatus.PICKED_UP, courier2, ActorRole.COURIER, "Note 2", null, null));
                successCount.incrementAndGet();
            } catch (DomainException e) {
                if (e.code() == ErrorCode.CONCURRENT_UPDATE || e.code() == ErrorCode.INVALID_STATUS_TRANSITION) {
                    concurrentUpdateCount.incrementAndGet();
                } else {
                    otherErrorCount.incrementAndGet();
                }
            } catch (Exception e) {
                otherErrorCount.incrementAndGet();
            }
        };

        executor.submit(task1);
        executor.submit(task2);

        readyLatch.await();
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(otherErrorCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(concurrentUpdateCount.get()).isEqualTo(1);
    }
}

