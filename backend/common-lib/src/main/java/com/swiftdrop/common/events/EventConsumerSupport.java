package com.swiftdrop.common.events;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.swiftdrop.common.web.RequestIdFilter;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Consumer side of SRS 3.5, used from {@code @RabbitListener} methods. Every {@code @RabbitListener} in a service
 * already runs with manual acknowledgement and prefetch 10 (see {@link EventsAutoConfiguration}).
 *
 * <pre>{@code
 * @RabbitListener(queues = EventTopology.DELIVERY_QUEUE)
 * void onMessage(Message message, Channel channel) throws IOException {
 *     consumer.handle(message, channel, handlers::handle);   // handlers.handle is @Transactional, on another bean
 * }
 * }</pre>
 *
 * <p>A handler gets 3 attempts in total, waiting 1 s and then 5 s. Success acknowledges the message; after the last
 * failure it is rejected without requeue and goes to the {@code <queue>.dlq} dead-letter queue. A message that cannot
 * be read as an event can never succeed, so it goes to the dead-letter queue at once. Each attempt must be its own
 * transaction, so the rows of a failed attempt (including {@code processed_events}) roll back before the next one.
 */
public class EventConsumerSupport {

    public static final int MAX_ATTEMPTS = 3;
    public static final int PREFETCH = 10;
    public static final long DEFAULT_INITIAL_DELAY_MS = 1000;
    static final double BACKOFF_MULTIPLIER = 5;

    private static final Logger log = LoggerFactory.getLogger(EventConsumerSupport.class);

    /** Business handling of one event; {@code envelope.data()} is the data record of its event type. */
    @FunctionalInterface
    public interface EventHandler {
        void handle(EventEnvelope<?> envelope) throws Exception;
    }

    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;

    /** @param initialDelayMs wait before the second attempt; the wait before the third is five times longer */
    public EventConsumerSupport(ObjectMapper objectMapper, long initialDelayMs) {
        if (initialDelayMs <= 0) {
            throw new IllegalArgumentException("initialDelayMs must be positive");
        }
        this.objectMapper = objectMapper;
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(initialDelayMs);
        backOff.setMultiplier(BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(Math.round(initialDelayMs * BACKOFF_MULTIPLIER));
        this.retryTemplate = new RetryTemplate();
        this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(MAX_ATTEMPTS, Map.of(Exception.class, true), true));
        this.retryTemplate.setBackOffPolicy(backOff);
    }

    /** Runs the handler with the retry policy, then acknowledges or dead-letters the message. */
    public void handle(Message message, Channel channel, EventHandler handler) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        EventEnvelope<?> envelope;
        try {
            envelope = read(message);
        } catch (IOException | RuntimeException e) {
            // Class name only: a parser message can quote the body (BR-28).
            log.warn("Unreadable event moved to the dead-letter queue ({})", e.getClass().getSimpleName());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        String previousRequestId = MDC.get(RequestIdFilter.MDC_KEY);
        MDC.put(RequestIdFilter.MDC_KEY, envelope.correlationId().toString());   // NFR-OBS-002
        try {
            process(envelope, deliveryTag, channel, handler);
        } finally {
            if (previousRequestId == null) {
                MDC.remove(RequestIdFilter.MDC_KEY);
            } else {
                MDC.put(RequestIdFilter.MDC_KEY, previousRequestId);
            }
        }
    }

    private void process(EventEnvelope<?> envelope, long deliveryTag, Channel channel, EventHandler handler)
            throws IOException {
        try {
            retryTemplate.<Void, Exception>execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.info("Retrying event {} ({}), attempt {} of {}", envelope.eventId(), envelope.eventType(),
                            context.getRetryCount() + 1, MAX_ATTEMPTS);
                }
                handler.handle(envelope);
                return null;
            });
        } catch (BackOffInterruptedException e) {
            // Shutting down while waiting (NFR-REL-004): this is not a failure of the event, so it goes back.
            channel.basicNack(deliveryTag, false, true);
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            // Class name only: exception messages can contain personal data (BR-28, NFR-SEC-011).
            log.warn("Event {} ({}) failed after {} attempts and moves to the dead-letter queue ({})",
                    envelope.eventId(), envelope.eventType(), MAX_ATTEMPTS, e.getClass().getSimpleName());
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    /** The envelope with its data typed by the {@code eventType} field; unknown types are rejected. */
    private EventEnvelope<?> read(Message message) throws IOException {
        JsonNode tree = objectMapper.readTree(message.getBody());
        Class<?> dataType = EventTypes.dataType(tree.path("eventType").asText(null));
        JavaType envelopeType = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, dataType);
        return objectMapper.readValue(message.getBody(), envelopeType);
    }
}
