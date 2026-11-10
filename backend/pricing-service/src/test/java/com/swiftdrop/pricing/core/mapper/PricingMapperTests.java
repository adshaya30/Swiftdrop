package com.swiftdrop.pricing.core.mapper;

import com.swiftdrop.pricing.core.dto.FeeBreakdown;
import com.swiftdrop.pricing.domain.*;
import com.swiftdrop.common.enums.ParcelSize;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.assertj.core.api.Assertions.assertThat;

class PricingMapperTests {
    private final PricingMapper mapper = Mappers.getMapper(PricingMapper.class);

    @Test
    @DisplayName("FR-PRC-002/003/005: map reference and fee fields without exposing entities")
    void mapsResponses() {
        var western = new Zone("WESTERN", "Western", true);
        var origin = new District("COLOMBO", "Colombo", western);
        var destination = new District("KANDY", "Kandy", new Zone("CENTRAL", "Central", true));
        var fees = new FeeBreakdown(new BigDecimal("500.00"), new BigDecimal("150.00"), new BigDecimal("150.00"),
                new BigDecimal("80.00"), new BigDecimal("800.00"), new BigDecimal("720.00"));
        var internal = mapper.internal(origin, destination, fees, BigDecimal.TEN);
        assertThat(internal.currency()).isEqualTo("LKR");
        assertThat(internal.originDistrict().code()).isEqualTo("COLOMBO");
        assertThat(internal.destinationDistrict().name()).isEqualTo("Kandy");
        assertThat(internal.originZone().name()).isEqualTo("Western");
        assertThat(internal.subtotal()).isEqualByComparingTo("800");
        assertThat(internal.discountPercent()).isEqualByComparingTo("10");
        assertThat(mapper.quote(origin, destination, fees).total()).isEqualByComparingTo("720");
        assertThat(mapper.districtOption(origin).zoneCode()).isEqualTo("WESTERN");
        assertThat(mapper.size(new SizeSurcharge(ParcelSize.SMALL, BigDecimal.ZERO, "30 x 20 x 10 cm")).description())
                .isEqualTo("30 x 20 x 10 cm");
    }
}
