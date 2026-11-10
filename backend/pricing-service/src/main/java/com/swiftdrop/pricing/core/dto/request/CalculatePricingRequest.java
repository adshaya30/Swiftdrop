package com.swiftdrop.pricing.core.dto.request;

import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.pricing.core.validation.ValidParcelWeight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CalculatePricingRequest(
        @NotBlank @Size(max = 20) @Schema(example = "COLOMBO") String originDistrictCode,
        @NotBlank @Size(max = 20) @Schema(example = "KANDY") String destinationDistrictCode,
        @NotNull @ValidParcelWeight @Schema(example = "2.00", minimum = "0.01",
                description = "At most two decimal places; maximum comes from pricing configuration") BigDecimal weightKg,
        @NotNull ParcelSize size,
        @DecimalMin("0") @DecimalMax("50") @Schema(defaultValue = "0", minimum = "0", maximum = "50") BigDecimal discountPercent) {
    public CalculatePricingRequest {
        if (discountPercent == null) discountPercent = BigDecimal.ZERO;
    }
}
