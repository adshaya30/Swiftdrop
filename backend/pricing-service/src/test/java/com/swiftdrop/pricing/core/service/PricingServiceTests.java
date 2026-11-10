package com.swiftdrop.pricing.core.service;

import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.pricing.core.config.PricingProperties;
import com.swiftdrop.pricing.core.dto.request.*;
import com.swiftdrop.pricing.core.mapper.PricingMapper;
import com.swiftdrop.pricing.domain.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("TST-010")
class PricingServiceTests {
    private final DistrictRepository districts = mock(DistrictRepository.class);
    private final ZonePricingRepository prices = mock(ZonePricingRepository.class);
    private final SizeSurchargeRepository sizes = mock(SizeSurchargeRepository.class);
    private final Zone originZone = mock(Zone.class);
    private final Zone destinationZone = mock(Zone.class);
    private final District origin = new District("COLOMBO", "Colombo", originZone);
    private final District destination = new District("KANDY", "Kandy", destinationZone);
    private final PricingService service = new PricingService(districts, prices, sizes, new FeeCalculator(),
            Mappers.getMapper(PricingMapper.class), new PricingProperties(new BigDecimal("45")));

    @BeforeEach
    void referenceData() {
        when(originZone.getId()).thenReturn(UUID.randomUUID());
        when(destinationZone.getId()).thenReturn(UUID.randomUUID());
        when(originZone.getCode()).thenReturn("WESTERN");
        when(originZone.getName()).thenReturn("Western");
        when(destinationZone.getCode()).thenReturn("CENTRAL");
        when(destinationZone.getName()).thenReturn("Central");
        when(originZone.isActive()).thenReturn(true);
        when(destinationZone.isActive()).thenReturn(true);
        when(districts.findById("COLOMBO")).thenReturn(Optional.of(origin));
        when(districts.findById("KANDY")).thenReturn(Optional.of(destination));
        var price = new ZonePricing(originZone, destinationZone, new BigDecimal("500.00"), new BigDecimal("150.00"));
        when(prices.findById(any())).thenReturn(Optional.of(price));
        when(sizes.findById(ParcelSize.MEDIUM)).thenReturn(Optional.of(new SizeSurcharge(ParcelSize.MEDIUM,
                new BigDecimal("150.00"), "50 x 40 x 30 cm")));
    }

    @Test
    @DisplayName("TST-010 FR-PRC-002/003: share calculator and use a directed zone-pair key")
    void publicAndInternalCalculation() {
        var quote = service.quote(new QuoteRequest("COLOMBO", "KANDY", new BigDecimal("2"), ParcelSize.MEDIUM));
        assertThat(quote.total()).isEqualByComparingTo("800.00");
        assertThat(quote.discountAmount()).isEqualByComparingTo("0.00");
        var internal = service.calculate(new CalculatePricingRequest("COLOMBO", "KANDY", new BigDecimal("2"), ParcelSize.MEDIUM, BigDecimal.TEN));
        assertThat(internal.total()).isEqualByComparingTo("720.00");
        assertThat(internal.originDistrict().name()).isEqualTo("Colombo");
        verify(prices, times(2)).findById(new ZonePricingId(originZone.getId(), destinationZone.getId()));
    }

    @Test
    @DisplayName("TST-010 FR-PRC-004: unknown origin or destination is a field validation error")
    void unknownDistricts() {
        when(districts.findById("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.quote(new QuoteRequest("UNKNOWN", "KANDY", BigDecimal.ONE, ParcelSize.SMALL)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.errors()).singleElement().extracting(com.swiftdrop.common.error.FieldError::field).isEqualTo("originDistrictCode");
                });
        assertThatThrownBy(() -> service.quote(new QuoteRequest("COLOMBO", "UNKNOWN", BigDecimal.ONE, ParcelSize.SMALL)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errors().get(0).field()).isEqualTo("destinationDistrictCode"));
        verifyNoInteractions(prices);
    }

    @Test
    @DisplayName("TST-010 FR-PRC-004: either inactive zone is rejected before pricing lookup")
    void inactiveZones() {
        when(originZone.isActive()).thenReturn(false);
        assertNotServiceable();
        when(originZone.isActive()).thenReturn(true);
        when(destinationZone.isActive()).thenReturn(false);
        assertNotServiceable();
        verifyNoInteractions(prices);
    }

    private void assertNotServiceable() {
        assertThatThrownBy(() -> service.quote(new QuoteRequest("COLOMBO", "KANDY", BigDecimal.ONE, ParcelSize.SMALL)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ZONE_NOT_SERVICEABLE));
    }

    @Test
    @DisplayName("TST-010 FR-PRC-004: missing directed pricing row is PRICING_NOT_CONFIGURED")
    void missingPrice() {
        when(prices.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.quote(new QuoteRequest("COLOMBO", "KANDY", BigDecimal.ONE, ParcelSize.SMALL)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PRICING_NOT_CONFIGURED));
    }

    @Test
    @DisplayName("FR-PRC-005/009: use configured maximum, active-district query and size order")
    void listsReferenceData() {
        when(districts.findByZoneActiveTrueOrderByNameAsc()).thenReturn(List.of(origin, destination));
        when(sizes.findAll()).thenReturn(List.of(new SizeSurcharge(ParcelSize.LARGE, new BigDecimal("400"), "Large"),
                new SizeSurcharge(ParcelSize.SMALL, BigDecimal.ZERO, "Small")));
        var data = service.districts();
        assertThat(data.maxWeightKg()).isEqualByComparingTo("45");
        assertThat(data.districts()).extracting(d -> d.zoneCode()).containsExactly("WESTERN", "CENTRAL");
        assertThat(data.sizes()).extracting(s -> s.size()).containsExactly(ParcelSize.SMALL, ParcelSize.LARGE);
    }
}
