package com.swiftdrop.pricing.core.dto.response;

import java.math.BigDecimal;

/** FR-PRC-003: adds the district snapshots and pre-discount subtotal needed by parcel-service. */
public record InternalPricingResponse(String currency, ZoneDto originZone, ZoneDto destinationZone,
        BigDecimal baseFee, BigDecimal weightFee, BigDecimal sizeSurcharge, BigDecimal discountAmount,
        BigDecimal total, DistrictDto originDistrict, DistrictDto destinationDistrict,
        BigDecimal subtotal, BigDecimal discountPercent) { }
