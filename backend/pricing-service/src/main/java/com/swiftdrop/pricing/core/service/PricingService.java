package com.swiftdrop.pricing.core.service;

import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.pricing.core.config.PricingProperties;
import com.swiftdrop.pricing.core.dto.request.*;
import com.swiftdrop.pricing.core.dto.response.*;
import com.swiftdrop.pricing.core.mapper.PricingMapper;
import com.swiftdrop.pricing.domain.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PricingService {
    private final DistrictRepository districts;
    private final ZonePricingRepository prices;
    private final SizeSurchargeRepository sizes;
    private final FeeCalculator calculator;
    private final PricingMapper mapper;
    private final PricingProperties properties;

    public PricingService(DistrictRepository districts, ZonePricingRepository prices, SizeSurchargeRepository sizes,
                          FeeCalculator calculator, PricingMapper mapper, PricingProperties properties) {
        this.districts = districts;
        this.prices = prices;
        this.sizes = sizes;
        this.calculator = calculator;
        this.mapper = mapper;
        this.properties = properties;
    }

    public QuoteResponse quote(QuoteRequest request) {
        var result = calculate(request.originDistrictCode(), request.destinationDistrictCode(), request.weightKg(),
                request.size(), BigDecimal.ZERO);
        return mapper.quote(result.origin(), result.destination(), result.fees());
    }

    public InternalPricingResponse calculate(CalculatePricingRequest request) {
        var result = calculate(request.originDistrictCode(), request.destinationDistrictCode(), request.weightKg(),
                request.size(), request.discountPercent());
        return mapper.internal(result.origin(), result.destination(), result.fees(), request.discountPercent());
    }

    /** FR-PRC-005: expose active districts only, using the same configured limit as validation. */
    public DistrictsResponse districts() {
        var options = sizes.findAll().stream().sorted(Comparator.comparing(SizeSurcharge::getSize)).map(mapper::size).toList();
        var active = districts.findByZoneActiveTrueOrderByNameAsc().stream().map(mapper::districtOption).toList();
        return new DistrictsResponse(properties.maxParcelWeightKg(), options, active);
    }

    private Calculation calculate(String originCode, String destinationCode, BigDecimal weight, ParcelSize size, BigDecimal discount) {
        District origin = district(originCode, "originDistrictCode");
        District destination = district(destinationCode, "destinationDistrictCode");
        // FR-PRC-004: check serviceability before attempting the directed zone-pair lookup.
        if (!origin.getZone().isActive() || !destination.getZone().isActive()) {
            throw new DomainException(ErrorCode.ZONE_NOT_SERVICEABLE, "One or both districts are outside the serviceable zones");
        }
        ZonePricing price = prices.findById(new ZonePricingId(origin.getZone().getId(), destination.getZone().getId()))
                .orElseThrow(() -> new DomainException(ErrorCode.PRICING_NOT_CONFIGURED, "Pricing is not configured for these zones"));
        SizeSurcharge surcharge = sizes.findById(size)
                .orElseThrow(() -> new IllegalStateException("Required size reference data is missing"));
        return new Calculation(origin, destination, calculator.calculate(weight, price.getBaseFee(), price.getPerKgFee(),
                surcharge.getSurcharge(), discount));
    }

    private District district(String code, String field) {
        return districts.findById(code).orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR,
                "Request validation failed", List.of(new FieldError(field, "Unknown district code"))));
    }

    private record Calculation(District origin, District destination, com.swiftdrop.pricing.core.dto.FeeBreakdown fees) { }
}
