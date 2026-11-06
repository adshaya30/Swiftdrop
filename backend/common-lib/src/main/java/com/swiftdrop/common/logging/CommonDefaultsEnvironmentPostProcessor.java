package com.swiftdrop.common.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lowest-precedence defaults for every service: JSON logging on stdout (level from LOG_LEVEL), strict JSON input,
 * database start-up tolerance and health groups (readiness covers the database only, never the broker).
 * Any value in a service's application.yml or environment wins over these.
 */
public class CommonDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "swiftdropCommonDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("logging.structured.format.console", "logstash");
        d.put("logging.structured.json.add.service", "${spring.application.name:unknown}");
        d.put("logging.level.root", "${LOG_LEVEL:INFO}");
        d.put("spring.jackson.deserialization.fail-on-unknown-properties", "true");
        d.put("spring.datasource.hikari.initialization-fail-timeout", "120000");
        d.put("management.endpoint.health.probes.enabled", "true");
        d.put("management.endpoint.health.group.liveness.include", "livenessState");
        d.put("management.endpoint.health.group.readiness.include", "readinessState,db");
        d.put("management.endpoint.health.validate-group-membership", "false");
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, d));
    }
}
