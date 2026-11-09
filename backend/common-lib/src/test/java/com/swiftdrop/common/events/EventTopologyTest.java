package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;

@Tag("SRS-3.5")
class EventTopologyTest {

    private final Declarables declarables = EventTopology.declarables();

    @Test
    @DisplayName("SRS 3.5: swiftdrop.events is a durable topic exchange and swiftdrop.dlx a durable direct exchange")
    void exchanges() {
        Map<String, Exchange> exchanges = declarables.getDeclarablesByType(Exchange.class).stream()
                .collect(Collectors.toMap(Exchange::getName, e -> e));
        assertThat(exchanges).containsOnlyKeys("swiftdrop.events", "swiftdrop.dlx");
        assertThat(exchanges.get("swiftdrop.events").getType()).isEqualTo(ExchangeTypes.TOPIC);
        assertThat(exchanges.get("swiftdrop.dlx")).isInstanceOf(DirectExchange.class);
        assertThat(exchanges.values()).allMatch(Exchange::isDurable).noneMatch(Exchange::isAutoDelete);
    }

    @Test
    @DisplayName("SRS 3.5: the three queues are durable and dead-letter to swiftdrop.dlx with key <queue>.dlq")
    void queues() {
        Map<String, Queue> queues = queuesByName();
        for (String name : List.of("tracking.events", "delivery.events", "notification.events")) {
            Queue q = queues.get(name);
            assertThat(q.isDurable()).isTrue();
            assertThat(q.isAutoDelete()).isFalse();
            assertThat(q.isExclusive()).isFalse();
            assertThat(q.getArguments())
                    .containsEntry("x-dead-letter-exchange", "swiftdrop.dlx")
                    .containsEntry("x-dead-letter-routing-key", name + ".dlq");
        }
    }

    @Test
    @DisplayName("SRS 3.5, 6.5: the three DLQs are durable, bound to swiftdrop.dlx, and expire messages after 3 days")
    void deadLetterQueues() {
        Map<String, Queue> queues = queuesByName();
        assertThat(queues).containsOnlyKeys("tracking.events", "delivery.events", "notification.events",
                "tracking.events.dlq", "delivery.events.dlq", "notification.events.dlq");
        for (String name : List.of("tracking.events.dlq", "delivery.events.dlq", "notification.events.dlq")) {
            assertThat(queues.get(name).isDurable()).isTrue();
            assertThat(queues.get(name).getArguments()).containsEntry("x-message-ttl", 259_200_000);
            assertThat(bindingKeys("swiftdrop.dlx", name)).containsExactly(name);
        }
    }

    @Test
    @DisplayName("SRS 3.5: each queue is bound to exactly its routing keys")
    void bindings() {
        assertThat(bindingKeys("swiftdrop.events", "tracking.events")).containsExactlyInAnyOrder(
                "parcel.created", "parcel.status.changed", "delivery.assigned", "delivery.unassigned",
                "audit.recorded");
        assertThat(bindingKeys("swiftdrop.events", "delivery.events")).containsExactlyInAnyOrder(
                "parcel.created", "parcel.status.changed", "courier.changed");
        assertThat(bindingKeys("swiftdrop.events", "notification.events")).containsExactlyInAnyOrder(
                "parcel.created", "parcel.status.changed", "merchant.status.changed");
    }

    private Map<String, Queue> queuesByName() {
        return declarables.getDeclarablesByType(Queue.class).stream()
                .collect(Collectors.toMap(Queue::getName, q -> q));
    }

    private List<String> bindingKeys(String exchange, String queue) {
        return declarables.getDeclarablesByType(Binding.class).stream()
                .filter(b -> b.getExchange().equals(exchange) && b.getDestination().equals(queue))
                .map(Binding::getRoutingKey)
                .toList();
    }
}
