package com.swiftdrop.common.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * The broker topology of SRS 3.5, declared identically by every publisher and consumer so services start in any order
 * and nobody declares a queue with different arguments.
 */
public final class EventTopology {

    public static final String EXCHANGE = "swiftdrop.events";
    public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dlx";

    public static final String TRACKING_QUEUE = "tracking.events";
    public static final String DELIVERY_QUEUE = "delivery.events";
    public static final String NOTIFICATION_QUEUE = "notification.events";

    /** Dead-letter messages expire after 3 days (SRS 3.5, 6.5). */
    public static final long DLQ_MESSAGE_TTL_MS = 3L * 24 * 60 * 60 * 1000;

    /** Queue name to the routing keys it is bound to (SRS 3.5). */
    public static final Map<String, List<String>> BINDINGS = Map.of(
            TRACKING_QUEUE, List.of(EventTypes.PARCEL_CREATED, EventTypes.PARCEL_STATUS_CHANGED,
                    EventTypes.DELIVERY_ASSIGNED, EventTypes.DELIVERY_UNASSIGNED, EventTypes.AUDIT_RECORDED),
            DELIVERY_QUEUE, List.of(EventTypes.PARCEL_CREATED, EventTypes.PARCEL_STATUS_CHANGED,
                    EventTypes.COURIER_CHANGED),
            NOTIFICATION_QUEUE, List.of(EventTypes.PARCEL_CREATED, EventTypes.PARCEL_STATUS_CHANGED,
                    EventTypes.MERCHANT_STATUS_CHANGED));

    private EventTopology() {
    }

    /** {@code <queue>.dlq}: both the dead-letter routing key and the name of the dead-letter queue. */
    public static String dlqName(String queue) {
        return queue + ".dlq";
    }

    /** Exchanges, the three queues with their dead-letter queues, and all bindings. */
    public static Declarables declarables() {
        TopicExchange events = new TopicExchange(EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
        List<Declarable> all = new ArrayList<>(List.of(events, dlx));
        for (String name : List.of(TRACKING_QUEUE, DELIVERY_QUEUE, NOTIFICATION_QUEUE)) {
            Queue queue = QueueBuilder.durable(name)
                    .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(dlqName(name))
                    .build();
            Queue dlq = QueueBuilder.durable(dlqName(name))
                    .ttl((int) DLQ_MESSAGE_TTL_MS)
                    .build();
            all.add(queue);
            all.add(dlq);
            all.add(BindingBuilder.bind(dlq).to(dlx).with(dlqName(name)));
            for (String key : BINDINGS.get(name)) {
                Binding binding = BindingBuilder.bind(queue).to(events).with(key);
                all.add(binding);
            }
        }
        return new Declarables(all);
    }
}
