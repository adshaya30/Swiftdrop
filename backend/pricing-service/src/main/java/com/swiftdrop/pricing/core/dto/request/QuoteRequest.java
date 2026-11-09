package com.swiftdrop.pricing.core.dto.request;

import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.pricing.core.validation.ValidParcelWeight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record QuoteRequest(
        @NotBlank @Size(max = 20) @Schema(example = "COLOMBO") String originDistrictCode,
        @NotBlank @Size(max = 20) @Schema(example = "KANDY") String destinationDistrictCode,
        @NotNull @ValidParcelWeight @Schema(example = "2.00", minimum = "0.01",
                description = "At most two decimal places; maximum is returned by GET /api/v1/public/districts") BigDecimal weightKg,
        @NotNull ParcelSize size) { }
