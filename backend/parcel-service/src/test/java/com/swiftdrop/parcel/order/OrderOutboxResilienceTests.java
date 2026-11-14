package com.swiftdrop.parcel.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.JdbcOutboxEventRepository;
import com.swiftdrop.common.events.OutboxPublisher;
import com.swiftdrop.parcel.create.service.TrackingNumberGenerator;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import com.swiftdrop.parcel.support.ParcelFixtures;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TST-025 parcel part. The service context runs with RabbitMQ unreachable (see AbstractParcelDatabaseTest), like a
 * broker outage. A broker that becomes available afterwards then receives the stored event through the same
 * PostgreSQL outbox.
 */
@Tag("TST-025")
class OrderOutboxResilienceTests extends AbstractParcelDatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Value("${INTERNAL_API_KEY}") String internalKey;

    private UUID parcelId;

    @AfterEach
    void cleanup() {
        if (parcelId != null) {
            jdbc.update("DELETE FROM outbox_events WHERE payload->'data'->>'parcelId' = ?", parcelId.toString());
            jdbc.update("DELETE FROM parcels WHERE id = ?", parcelId);
        }
    }

    @Test
    @DisplayName("TST-025 NFR-REL-001: with RabbitMQ down a courier transition succeeds; the event waits, then is published")
    void transitionSucceedsWhileBrokerIsDown() throws Exception {
        Parcel parcel = parcels.saveAndFlush(ParcelFixtures.parcel(new TrackingNumberGenerator().generate(),
                UUID.randomUUID(), null, null));
        parcelId = parcel.getId();

        mvc.perform(post("/internal/v1/parcels/" + parcelId + "/transition").header("X-Internal-Key", internalKey)
                        .contentType("application/json")
                        .content("{\"toStatus\":\"PICKED_UP\",\"actorUserId\":\"" + UUID.randomUUID()
                                + "\",\"actorRole\":\"COURIER\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcelId))
                .isEqualTo("PICKED_UP");
        // The service's own publisher runs every second against the unreachable broker: the row stays unpublished.
        // (Its failure counter may grow on an older row instead, because each batch stops at the first failure.)
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).until(() -> publishedAt() == null);

        try (RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"))) {
            rabbit.start();
            CachingConnectionFactory factory = new CachingConnectionFactory(rabbit.getHost(), rabbit.getAmqpPort());
            factory.setUsername(rabbit.getAdminUsername());
            factory.setPassword(rabbit.getAdminPassword());
            factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);
            try {
                declareTopology(factory);
                OutboxPublisher publisher = new OutboxPublisher(new JdbcOutboxEventRepository(jdbc), factory,
                        new TransactionTemplate(transactionManager), 1000, Clock.systemUTC(), null);

                await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500)).until(() -> {
                    publisher.publishBatch();
                    return publishedAt() != null;
                });

                RabbitTemplate template = new RabbitTemplate(factory);
                Message message = null;
                for (int i = 0; i < 200 && message == null; i++) {
                    Message next = template.receive(EventTopology.DELIVERY_QUEUE, 2000);
                    if (next == null) {
                        break;
                    }
                    if (new String(next.getBody()).contains(parcelId.toString())) {
                        message = next;
                    }
                }
                assertThat(message).isNotNull();
                assertThat(message.getMessageProperties().getType()).isEqualTo("parcel.status.changed");
                assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId());
            } finally {
                factory.destroy();
            }
        }
    }

    private Object publishedAt() {
        return jdbc.queryForObject("SELECT published_at FROM outbox_events WHERE event_type = 'parcel.status.changed' "
                + "AND payload->'data'->>'parcelId' = ?", Object.class, parcelId.toString());
    }

    private String eventId() {
        return jdbc.queryForObject("SELECT id::text FROM outbox_events WHERE event_type = 'parcel.status.changed' "
                + "AND payload->'data'->>'parcelId' = ?", String.class, parcelId.toString());
    }

    private static void declareTopology(CachingConnectionFactory factory) {
        RabbitAdmin admin = new RabbitAdmin(factory);
        var declarables = EventTopology.declarables();
        declarables.getDeclarablesByType(Exchange.class).forEach(admin::declareExchange);
        declarables.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
        declarables.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
    }
}
