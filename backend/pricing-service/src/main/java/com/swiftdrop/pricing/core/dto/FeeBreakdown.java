package com.swiftdrop.pricing.core.dto;

import java.math.BigDecimal;

/** BR-08 calculation result, mapped to the two different SRS response contracts. */
public record FeeBreakdown(BigDecimal baseFee, BigDecimal weightFee, BigDecimal sizeSurcharge,
                           BigDecimal discountAmount, BigDecimal subtotal, BigDecimal total) { }
