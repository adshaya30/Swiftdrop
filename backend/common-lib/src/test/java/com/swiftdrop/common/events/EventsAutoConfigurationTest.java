package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

/** A service gets the events beans from the dependency alone; nothing is created that the service cannot use. */
@Tag("SRS-3.4")
class EventsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventsAutoConfiguration.class))
            .withPropertyValues("OUTBOX_POLL_INTERVAL_MS=60000", "spring.application.name=test-service");

    private final ApplicationContextRunner withRabbit = runner
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class, EventsAutoConfiguration.class));

    @Test
    @DisplayName("SRS 3.4: without a broker or a database no events beans are created")
    void nothingWithoutInfrastructure() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(Declarables.class);
            assertThat(ctx).doesNotHaveBean(OutboxService.class);
            assertThat(ctx).doesNotHaveBean(OutboxPublisher.class);
        });
    }

    @Test
    @DisplayName("SRS 3.5: with RabbitMQ the topology is declared and publisher confirms and returns are on")
    void topologyAndConfirms() {
        withRabbit.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(Declarables.class);
            CachingConnectionFactory factory = ctx.getBean(CachingConnectionFactory.class);
            assertThat(factory.isPublisherConfirms()).isTrue();
            assertThat(factory.isPublisherReturns()).isTrue();
            assertThat(ctx).doesNotHaveBean(OutboxService.class);
        });
    }

    @Test
    @DisplayName("SRS 3.4: with a database only, OutboxService and AuditEvents exist but no publisher")
    void outboxWithoutBroker() {
        runner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(OutboxService.class);
                    assertThat(ctx).hasSingleBean(AuditEvents.class);
                    assertThat(ctx).hasSingleBean(OutboxEventRepository.class);
                    assertThat(ctx).doesNotHaveBean(OutboxPublisher.class);
                });
    }

    @Test
    @DisplayName("SRS 3.5: with a database and RabbitMQ the outbox publisher runs")
    void publisherWithBoth() {
        withRabbit.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(OutboxPublisher.class);
                    assertThat(ctx.getBean(OutboxPublisher.class).isRunning()).isTrue();
                });
    }
}
