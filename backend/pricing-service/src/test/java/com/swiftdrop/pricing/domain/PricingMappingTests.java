package com.swiftdrop.pricing.domain;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PricingMappingTests {
    @Test
    @DisplayName("SRS 6.3: Hibernate accepts all pricing mappings and composite associations")
    void metadataBootstrapsWithoutDatabase() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false).build();
        try {
            var metadata = (MetadataImplementor) new MetadataSources(registry)
                    .addAnnotatedClass(Zone.class).addAnnotatedClass(District.class)
                    .addAnnotatedClass(ZonePricing.class).addAnnotatedClass(SizeSurcharge.class).buildMetadata();
            metadata.validate();
            assertThat(metadata.getEntityBindings()).hasSize(4);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
