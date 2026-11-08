package com.swiftdrop.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Events part of common-lib (SRS 3.4, 3.5). A service gets, with no extra wiring:
 * <ul>
 *   <li>with RabbitMQ: the {@link EventTopology} declarations (declared on every (re)connection, so a missing or down
 *       broker never stops start-up) and publisher confirms and returns on the connection factory;</li>
 *   <li>with a DataSource: {@link OutboxService} and {@link AuditEvents};</li>
 *   <li>with both: the {@link OutboxPublisher} (idle in services that have no {@code outbox_events} table).</li>
 * </ul>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration",
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"})
public class EventsAutoConfiguration {

    static final String POLL_INTERVAL_PROPERTY = "OUTBOX_POLL_INTERVAL_MS";
    static final long DEFAULT_POLL_INTERVAL_MS = 1000;

    /** Publisher confirms and returns are required by the outbox publisher (SRS 3.5). */
    @Bean
    @ConditionalOnClass(CachingConnectionFactory.class)
    static BeanPostProcessor swiftDropPublisherConfirms() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof CachingConnectionFactory factory) {
                    if (!factory.isPublisherConfirms() && !factory.isSimplePublisherConfirms()) {
                        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
                    }
                    factory.setPublisherReturns(true);
                }
                return bean;
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(ConnectionFactory.class)
    static class TopologyConfiguration {

        @Bean
        Declarables swiftDropEventTopology() {
            return EventTopology.declarables();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean({DataSource.class, PlatformTransactionManager.class})
    static class OutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OutboxEventRepository swiftDropOutboxEventRepository(DataSource dataSource) {
            return new JdbcOutboxEventRepository(new JdbcTemplate(dataSource));
        }

        @Bean
        @ConditionalOnMissingBean
        OutboxService swiftDropOutboxService(OutboxEventRepository repository, ObjectProvider<ObjectMapper> objectMapper) {
            return new OutboxService(repository, objectMapper.getIfAvailable(EventsAutoConfiguration::defaultMapper),
                    Clock.systemUTC());
        }

        @Bean
        @ConditionalOnMissingBean
        AuditEvents swiftDropAuditEvents(OutboxService outboxService, Environment env) {
            return new AuditEvents(outboxService, env.getProperty("spring.application.name"), Clock.systemUTC());
        }

        @Bean
        @ConditionalOnBean(ConnectionFactory.class)
        @ConditionalOnMissingBean
        OutboxPublisher swiftDropOutboxPublisher(OutboxEventRepository repository, ConnectionFactory connectionFactory,
                                        PlatformTransactionManager transactionManager, Environment env,
                                        ObjectProvider<MeterRegistry> meterRegistry) {
            long interval = env.getProperty(POLL_INTERVAL_PROPERTY, Long.class, DEFAULT_POLL_INTERVAL_MS);
            return new OutboxPublisher(repository, connectionFactory, new TransactionTemplate(transactionManager),
                    interval, Clock.systemUTC(), meterRegistry.getIfAvailable());
        }
    }

    static ObjectMapper defaultMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
