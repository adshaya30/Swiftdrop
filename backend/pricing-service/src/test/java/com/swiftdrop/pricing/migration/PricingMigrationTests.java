package com.swiftdrop.pricing.migration;

import com.swiftdrop.pricing.support.AbstractPricingDatabaseTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@Tag("TST-010")
class PricingMigrationTests extends AbstractPricingDatabaseTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("TST-010 FR-PRC-009: Appendix B counts, names and every directed matrix entry")
    void referenceDataMatchesAppendixB() {
        for (var entry : java.util.Map.of("zones", 6, "districts", 25, "zone_pricing", 36, "size_surcharges", 3).entrySet()) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + entry.getKey(), Integer.class)).isEqualTo(entry.getValue());
        }
        String[] zones = {"WESTERN", "CENTRAL", "SOUTHERN", "NORTH_EAST", "NW_NC", "UVA_SAB"};
        String[] names = {"Western", "Central", "Southern", "Northern & Eastern", "North Western & North Central", "Uva & Sabaragamuwa"};
        String[] districts = {"Colombo,Gampaha,Kalutara", "Kandy,Matale,Nuwara Eliya", "Galle,Matara,Hambantota",
                "Jaffna,Kilinochchi,Mannar,Vavuniya,Mullaitivu,Batticaloa,Ampara,Trincomalee",
                "Kurunegala,Puttalam,Anuradhapura,Polonnaruwa", "Badulla,Monaragala,Ratnapura,Kegalle"};
        int[][] base = {{350,500,500,800,550,550}, {500,350,650,750,550,550}, {500,650,350,950,750,600},
                {800,750,950,350,700,800}, {550,550,750,700,350,700}, {550,550,600,800,700,350}};
        for (int i = 0; i < zones.length; i++) {
            assertThat(jdbc.queryForObject("SELECT name FROM zones WHERE code = ? AND active", String.class, zones[i])).isEqualTo(names[i]);
            for (String district : districts[i].split(",")) {
                assertThat(jdbc.queryForObject("SELECT d.name FROM districts d JOIN zones z ON z.id=d.zone_id WHERE d.code=? AND z.code=?",
                        String.class, district.toUpperCase(java.util.Locale.ROOT).replace(' ', '_'), zones[i])).isEqualTo(district);
            }
            for (int j = 0; j < zones.length; j++) {
                var row = jdbc.queryForMap("SELECT p.base_fee, p.per_kg_fee FROM zone_pricing p JOIN zones o ON o.id=p.origin_zone_id "
                        + "JOIN zones d ON d.id=p.destination_zone_id WHERE o.code=? AND d.code=?", zones[i], zones[j]);
                assertThat((BigDecimal) row.get("base_fee")).isEqualByComparingTo(BigDecimal.valueOf(base[i][j]));
                assertThat((BigDecimal) row.get("per_kg_fee")).isEqualByComparingTo(BigDecimal.valueOf(i == j ? 100 : 150));
            }
        }
        String[] sizes = {"SMALL", "MEDIUM", "LARGE"};
        String[] descriptions = {"30 x 20 x 10 cm", "50 x 40 x 30 cm", "80 x 60 x 50 cm"};
        int[] surcharges = {0, 150, 400};
        for (int i = 0; i < sizes.length; i++) {
            assertThat(jdbc.queryForObject("SELECT description FROM size_surcharges WHERE size=?", String.class, sizes[i])).isEqualTo(descriptions[i]);
            assertThat(jdbc.queryForObject("SELECT surcharge FROM size_surcharges WHERE size=?", BigDecimal.class, sizes[i]))
                    .isEqualByComparingTo(BigDecimal.valueOf(surcharges[i]));
        }
    }

    @Test
    @DisplayName("SRS 6.3: pricing constraints and shared outbox index are enforced")
    void constraintsAreEnforced() {
        for (String column : List.of("base_fee", "per_kg_fee")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE zone_pricing SET " + column + "=-0.01"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE size_surcharges SET surcharge=-0.01 WHERE size='SMALL'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO zones(id,code,name) VALUES (?,?,?)", java.util.UUID.randomUUID(), "WESTERN", "Duplicate"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO zones(id,code,name) VALUES (?,?,?)", java.util.UUID.randomUUID(), "DUPLICATE", "Western"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO districts(code,name,zone_id) VALUES (?,?,?)", "MISSING", "Missing", java.util.UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename='outbox_events'", String.class))
                .anySatisfy(index -> assertThat(index).contains("created_at").contains("published_at IS NULL"));
        assertThat(jdbc.queryForObject("SELECT to_regclass('processed_events') IS NULL", Boolean.class)).isTrue();
    }
}
