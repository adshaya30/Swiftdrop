package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.common.events.data.CourierChangedData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * TST-025 on a real broker that is really stopped and restarted. The port is fixed so that the same connection factory
 * reconnects after the restart, as it would in a running service. The outbox table is the in-memory double until the
 * PostgreSQL driver is on this module's test classpath.
 */
@Testcontainers
@Tag("TST-025")
class OutboxBrokerOutageTest {

    private static final int AMQP_PORT = freePort();

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"))
            .withExposedPorts(5672)
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(AMQP_PORT), new ExposedPort(5672))));

    @Test
    @DisplayName("TST-025 NFR-REL-001: with RabbitMQ stopped an event is stored and stays unpublished; after restart it is published")
    void eventWaitsForBroker() {
        CachingConnectionFactory factory = new CachingConnectionFactory(RABBIT.getHost(), AMQP_PORT);
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        try {
            declareTopology(factory);
            InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
            OutboxService outbox = new OutboxService(repository, EventsAutoConfiguration.defaultMapper(),
                    Clock.systemUTC());
            OutboxPublisher publisher = new OutboxPublisher(repository, factory,
                    TransactionOperations.withoutTransaction(), 1000, Clock.systemUTC(), null);

            RABBIT.getDockerClient().stopContainerCmd(RABBIT.getContainerId()).exec();

            UUID eventId = addEvent(outbox);
            assertThatCode(publisher::tick).doesNotThrowAnyException();
            assertThat(repository.get(eventId).publishedAt()).isNull();
            assertThat(repository.get(eventId).attempts()).isPositive();

            RABBIT.getDockerClient().startContainerCmd(RABBIT.getContainerId()).exec();

            await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1)).until(() -> {
                publisher.tick();
                return repository.get(eventId).publishedAt() != null;
            });
            Message message = new RabbitTemplate(factory).receive(EventTopology.DELIVERY_QUEUE, 10_000);
            assertThat(message).isNotNull();
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        } finally {
            factory.destroy();
        }
    }

    /** The business transaction: adding the event needs no broker. */
    private static UUID addEvent(OutboxService outbox) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            return outbox.add(EventTypes.COURIER_CHANGED, new CourierChangedData(UUID.randomUUID(), "Kamal Perera",
                    UserStatus.ACTIVE, VehicleType.VAN, "WP CAB-1234")).eventId();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static void declareTopology(CachingConnectionFactory factory) {
        RabbitAdmin admin = new RabbitAdmin(factory);
        var declarables = EventTopology.declarables();
        declarables.getDeclarablesByType(Exchange.class).forEach(admin::declareExchange);
        declarables.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
        declarables.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
