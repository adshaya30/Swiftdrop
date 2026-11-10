package com.swiftdrop.common.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sends committed outbox rows to {@code swiftdrop.events} (SRS 3.5). Every {@code OUTBOX_POLL_INTERVAL_MS} it locks
 * up to 100 unpublished rows (FOR UPDATE SKIP LOCKED, oldest first) and publishes them one by one as persistent,
 * mandatory messages, waiting for each publisher confirm. A confirmed row gets {@code published_at}; a returned
 * (unroutable), nacked or unconfirmed message leaves the row unpublished, increments {@code attempts} and ends the
 * batch, so a down broker is not hammered (NFR-REL-001, NFR-REL-002).
 *
 * <p>In a service without an {@code outbox_events} table (tracking, notification) it detects this once and stays idle.
 */
public class OutboxPublisher implements SmartLifecycle {

    static final int BATCH_SIZE = 100;
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);
    /** (S) Published rows are kept for one day (SRS 3.5, 6.5). */
    static final Duration RETENTION = Duration.ofDays(1);
    static final Duration CLEANUP_INTERVAL = Duration.ofDays(1);
    static final String BACKLOG_METRIC = "swiftdrop.outbox.backlog";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    enum State { UNKNOWN, ACTIVE, IDLE }

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionOperations transactions;
    private final long pollIntervalMs;
    private final Clock clock;
    private final AtomicLong backlog = new AtomicLong();
    private final boolean backlogMetric;

    private volatile State state = State.UNKNOWN;
    private volatile Instant lastCleanup;
    private ScheduledExecutorService executor;

    public OutboxPublisher(OutboxEventRepository repository, ConnectionFactory connectionFactory,
                           TransactionOperations transactions, long pollIntervalMs, Clock clock,
                           MeterRegistry meterRegistry) {
        if (pollIntervalMs <= 0) {
            throw new IllegalArgumentException("OUTBOX_POLL_INTERVAL_MS must be positive");
        }
        this.repository = repository;
        this.transactions = transactions;
        this.pollIntervalMs = pollIntervalMs;
        this.clock = clock;
        // A private template: a RabbitTemplate bean would make Spring Boot's own template back off.
        this.rabbitTemplate = new RabbitTemplate(connectionFactory);
        this.rabbitTemplate.setMandatory(true);
        this.backlogMetric = meterRegistry != null;
        if (meterRegistry != null) {
            Gauge.builder(BACKLOG_METRIC, backlog, AtomicLong::get)
                    .description("Unpublished rows in outbox_events")
                    .register(meterRegistry);
        }
    }

    /** One scheduled run. Never throws: a failure is logged and retried on the next run. */
    public void tick() {
        try {
            if (state == State.UNKNOWN) {
                state = repository.tableExists() ? State.ACTIVE : State.IDLE;
                if (state == State.IDLE) {
                    log.info("No outbox_events table in this service: the outbox publisher stays idle");
                }
            }
            if (state != State.ACTIVE) {
                return;
            }
            publishBatch();
            cleanupIfDue();
            if (backlogMetric) {
                backlog.set(repository.countUnpublished());
            }
        } catch (RuntimeException e) {
            // Database not reachable yet (NFR-REL-003) or a transient error: try again on the next run.
            log.warn("Outbox publishing run failed ({}); retrying in {} ms", e.getClass().getSimpleName(),
                    pollIntervalMs);
        }
    }

    /** Publishes one batch in one transaction and returns the number of rows confirmed. */
    public int publishBatch() {
        Integer published = transactions.execute(status -> {
            List<OutboxEvent> rows = repository.lockUnpublishedBatch(BATCH_SIZE);
            int confirmed = 0;
            for (OutboxEvent row : rows) {
                if (send(row)) {
                    repository.markPublished(row.id(), clock.instant());
                    confirmed++;
                } else {
                    repository.incrementAttempts(row.id());
                    break;
                }
            }
            return confirmed;
        });
        return published == null ? 0 : published;
    }

    boolean send(OutboxEvent row) {
        Message message = MessageBuilder.withBody(row.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(row.id().toString())
                .setCorrelationId(row.correlationId().toString())
                .setType(row.eventType())
                .build();
        CorrelationData correlation = new CorrelationData(row.id().toString());
        try {
            rabbitTemplate.send(EventTopology.EXCHANGE, row.routingKey(), message, correlation);
            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                log.warn("Event {} ({}) was unroutable and stays in the outbox", row.id(), row.eventType());
                return false;
            }
            if (!confirm.isAck()) {
                log.warn("Event {} ({}) was not accepted by the broker and stays in the outbox", row.id(),
                        row.eventType());
                return false;
            }
            return true;
        } catch (AmqpException | ExecutionException | TimeoutException e) {
            log.warn("Event {} ({}) not published ({}); it stays in the outbox", row.id(), row.eventType(),
                    e.getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** (S) Deletes published rows older than one day, at most once a day. */
    void cleanupIfDue() {
        Instant now = clock.instant();
        if (lastCleanup != null && lastCleanup.plus(CLEANUP_INTERVAL).isAfter(now)) {
            return;
        }
        int deleted = repository.deletePublishedBefore(now.minus(RETENTION));
        lastCleanup = now;
        if (deleted > 0) {
            log.info("Deleted {} published outbox rows older than {}", deleted, RETENTION);
        }
    }

    State state() {
        return state;
    }

    long backlog() {
        return backlog.get();
    }

    @Override
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-publisher");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** NFR-REL-004: finish the running batch, then stop. */
    @Override
    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(CONFIRM_TIMEOUT.toSeconds() + 5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }
}
