package com.swiftdrop.pricing.admin.dto;

import com.swiftdrop.common.enums.ParcelSize;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** FR-PRC-007: one element of the PUT /admin/size-surcharges list. */
public record SizeSurchargeUpdateItem(
        @NotNull ParcelSize size,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal surcharge) { }
