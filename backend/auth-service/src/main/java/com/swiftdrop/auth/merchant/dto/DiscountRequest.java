package com.swiftdrop.auth.merchant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Body of PATCH .../discount: 0.00 to 50.00 with at most two decimals (FR-AUTH-017). */
public record DiscountRequest(
        @NotNull(message = "Discount percent is required")
        @DecimalMin(value = "0.00", message = "Discount percent must be at least 0")
        @DecimalMax(value = "50.00", message = "Discount percent must be at most 50")
        @Digits(integer = 2, fraction = 2, message = "Discount percent must have at most two decimals")
        BigDecimal discountPercent) {
}
