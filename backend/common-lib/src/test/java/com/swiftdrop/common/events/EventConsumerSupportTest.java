package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.common.events.data.CourierChangedData;
import com.swiftdrop.common.web.RequestIdFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

@Tag("SRS-3.5")
class EventConsumerSupportTest {

    private static final long TAG = 42;

    private final ObjectMapper mapper = EventsAutoConfiguration.defaultMapper();
    private final Channel channel = mock(Channel.class);
    private final EventConsumerSupport support = new EventConsumerSupport(mapper, 1);
    private final AtomicInteger attempts = new AtomicInteger();

    private final CourierChangedData data = new CourierChangedData(UUID.randomUUID(), "Kamal Perera",
            UserStatus.ACTIVE, VehicleType.VAN, "WP CAB-1234");
    private final EventEnvelope<CourierChangedData> envelope =
            EventEnvelope.create(EventTypes.COURIER_CHANGED, Instant.now(), UUID.randomUUID(), data);

    @AfterEach
    void cleanUp() {
        MDC.clear();
        Thread.interrupted();
    }

    @Test
    @DisplayName("SRS 3.5: a handled event is acknowledged once and gets its typed data record")
    void successAcks() throws Exception {
        AtomicReference<Object> received = new AtomicReference<>();

        support.handle(message(envelope), channel, e -> received.set(e.data()));

        assertThat(received.get()).isEqualTo(data);
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("SRS 3.5: a handler that recovers within 3 attempts is acknowledged")
    void recoversWithinAttempts() throws Exception {
        support.handle(message(envelope), channel, e -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary");
            }
        });

        assertThat(attempts).hasValue(3);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    @DisplayName("SRS 3.5: after exactly 3 failed attempts the message is rejected without requeue")
    void exhaustedGoesToDlq() throws Exception {
        support.handle(message(envelope), channel, e -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always");
        });

        assertThat(attempts).hasValue(EventConsumerSupport.MAX_ATTEMPTS).hasValue(3);
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("SRS 3.5: RetryableEventException is retried and then dead-lettered like any failure")
    void retryableException() throws Exception {
        support.handle(message(envelope), channel, e -> {
            attempts.incrementAndGet();
            throw new RetryableEventException("parcel not known yet");
        });

        assertThat(attempts).hasValue(3);
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("SRS 3.5: checked exceptions from a handler are retried too")
    void checkedExceptions() throws Exception {
        support.handle(message(envelope), channel, e -> {
            attempts.incrementAndGet();
            throw new IOException("checked");
        });

        assertThat(attempts).hasValue(3);
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("SRS 3.5: the second wait is five times the first")
    void backOffDelays() throws Exception {
        EventConsumerSupport timed = new EventConsumerSupport(mapper, 40);
        long start = System.nanoTime();

        timed.handle(message(envelope), channel, e -> {
            throw new IllegalStateException("always");
        });

        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(40 + 200);
    }

    @Test
    @DisplayName("SRS 3.5: a message that is not JSON goes straight to the dead-letter queue, without attempts")
    void malformedJson() throws Exception {
        support.handle(raw("not json"), channel, e -> attempts.incrementAndGet());

        assertThat(attempts).hasValue(0);
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("SRS 3.5: an unknown event type goes straight to the dead-letter queue")
    void unknownEventType() throws Exception {
        String body = mapper.writeValueAsString(envelope).replace("courier.changed", "parcel.deleted");

        support.handle(raw(body), channel, e -> attempts.incrementAndGet());

        assertThat(attempts).hasValue(0);
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("SRS 3.5: an envelope with a missing required field goes straight to the dead-letter queue")
    void missingEnvelopeField() throws Exception {
        String body = "{\"eventType\":\"courier.changed\",\"schemaVersion\":1}";

        support.handle(raw(body), channel, e -> attempts.incrementAndGet());

        assertThat(attempts).hasValue(0);
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("NFR-OBS-002: the correlation id is in the MDC while handling and removed afterwards")
    void correlationInMdc() throws Exception {
        AtomicReference<String> during = new AtomicReference<>();

        support.handle(message(envelope), channel, e -> during.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(during.get()).isEqualTo(envelope.correlationId().toString());
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("NFR-OBS-002: an MDC value set before handling is restored, also after a failure")
    void mdcRestored() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "outer");

        support.handle(message(envelope), channel, e -> {
            throw new IllegalStateException("always");
        });

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("outer");
    }

    @Test
    @DisplayName("NFR-REL-004: interrupted while waiting to retry (shutdown), the message is requeued, not dead-lettered")
    void interruptedRequeues() throws Exception {
        support.handle(message(envelope), channel, e -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("fails during shutdown");
        });

        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicNack(TAG, false, false);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("SRS 3.5: the retry delay must be positive")
    void positiveDelay() {
        assertThatThrownBy(() -> new EventConsumerSupport(mapper, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private Message message(EventEnvelope<?> event) throws Exception {
        return raw(mapper.writeValueAsString(event));
    }

    private static Message raw(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).setDeliveryTag(TAG).build();
    }
}
