package com.swiftdrop.pricing.core.service;

import com.swiftdrop.pricing.core.dto.FeeBreakdown;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** FR-PRC-001 / BR-08: one exact decimal calculator, shared by public and internal pricing. */
@Component
public class FeeCalculator {
    public FeeBreakdown calculate(BigDecimal weightKg, BigDecimal baseFee, BigDecimal perKgFee,
                                  BigDecimal sizeSurcharge, BigDecimal discountPercent) {
        BigDecimal extraKg = weightKg.setScale(0, RoundingMode.CEILING).subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal weightFee = extraKg.multiply(perKgFee).setScale(2);
        BigDecimal subtotal = baseFee.add(weightFee).add(sizeSurcharge).setScale(2);
        BigDecimal total = subtotal.multiply(BigDecimal.ONE.subtract(discountPercent.movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        return new FeeBreakdown(baseFee.setScale(2), weightFee, sizeSurcharge.setScale(2),
                subtotal.subtract(total), subtotal, total);
    }
}
