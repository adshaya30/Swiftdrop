package com.swiftdrop.pricing.admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** FR-PRC-007: one directed zone pair of the pricing matrix. */
public record PricingRowResponse(UUID originZoneId, String originZoneCode, UUID destinationZoneId,
                                 String destinationZoneCode, BigDecimal baseFee, BigDecimal perKgFee) { }
