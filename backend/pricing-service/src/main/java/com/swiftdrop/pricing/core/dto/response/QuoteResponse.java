package com.swiftdrop.pricing.core.dto.response;

import java.math.BigDecimal;

/** FR-PRC-002 / SRS 7.5: public quotes never expose a discount input or internal-only fields. */
public record QuoteResponse(String currency, ZoneDto originZone, ZoneDto destinationZone,
                            BigDecimal baseFee, BigDecimal weightFee, BigDecimal sizeSurcharge,
                            BigDecimal discountAmount, BigDecimal total) { }
