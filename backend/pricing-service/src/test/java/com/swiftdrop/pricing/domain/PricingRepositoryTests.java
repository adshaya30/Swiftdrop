package com.swiftdrop.pricing.domain;

import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.pricing.support.AbstractPricingDatabaseTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PricingRepositoryTests extends AbstractPricingDatabaseTest {
    @Autowired ZoneRepository zones;
    @Autowired DistrictRepository districts;
    @Autowired ZonePricingRepository prices;
    @Autowired SizeSurchargeRepository sizes;
    @Autowired EntityManager em;

    @Test
    @DisplayName("FR-PRC-009: repositories map reference data, directed keys, and the shared size enum")
    void mapsReferenceData() {
        Zone western = zones.findByCode("WESTERN").orElseThrow();
        Zone central = zones.findByCode("CENTRAL").orElseThrow();
        assertThat(districts.findById("COLOMBO").orElseThrow().getZone().getId()).isEqualTo(western.getId());
        assertThat(districts.existsByZoneId(western.getId())).isTrue();
        assertThat(prices.findById(new ZonePricingId(western.getId(), central.getId())).orElseThrow().getBaseFee())
                .isEqualByComparingTo("500.00");
        assertThat(sizes.findById(ParcelSize.MEDIUM).orElseThrow().getSurcharge()).isEqualByComparingTo("150.00");
        assertThat(districts.findByZoneActiveTrueOrderByNameAsc()).hasSize(25);
        western.setActive(false);
        em.flush();
        assertThat(districts.findByZoneActiveTrueOrderByNameAsc()).hasSize(22);
    }

    @Test
    @DisplayName("SRS 6.3: persist pricing domains and keep zone codes immutable")
    void persistsDomains() {
        Zone zone = zones.saveAndFlush(new Zone("TEST_ZONE", "Test zone", true));
        UUID id = zone.getId();
        assertThat(id.version()).isEqualTo(4);
        assertThat(zone.getCreatedAt()).isNotNull();
        assertThat(zone.getUpdatedAt()).isNotNull();
        districts.saveAndFlush(new District("TEST_DISTRICT", "Test district", zone));
        ZonePricing price = prices.saveAndFlush(new ZonePricing(zone, zone, new BigDecimal("1.25"), new BigDecimal("2.50")));
        em.clear();
        assertThat(prices.findById(price.getId()).orElseThrow().getPerKgFee()).isEqualByComparingTo("2.50");
        Zone stored = zones.findById(id).orElseThrow();
        stored.setName("Renamed zone");
        em.flush();
        em.clear();
        assertThat(zones.findByCode("TEST_ZONE").orElseThrow().getName()).isEqualTo("Renamed zone");
    }
}
