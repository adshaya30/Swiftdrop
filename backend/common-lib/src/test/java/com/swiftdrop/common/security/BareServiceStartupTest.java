package com.swiftdrop.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.common.testapp.TestApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@Tag("TST-013")
class BareServiceStartupTest {

    @Test
    @DisplayName("TST-013 NFR-REL-003: a bare service (dependency + SecurityRules bean) starts with a strong key")
    void startsWithOnlyDependencyAndRules() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0", "INTERNAL_API_KEY=" + TestApp.INTERNAL_KEY)
                .run()) {
            assertThat(ctx.getBeansOfType(SecurityRules.class)).hasSize(1);
            assertThat(ctx.getBeansOfType(InternalApiKey.class)).hasSize(1);
        }
    }

    @Test
    @DisplayName("TST-013 start-up fails fast when INTERNAL_API_KEY is a placeholder")
    void failsFastWithWeakKey() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0", "INTERNAL_API_KEY=changeme")
                .run())
                .hasStackTraceContaining("INTERNAL_API_KEY");
    }
}
