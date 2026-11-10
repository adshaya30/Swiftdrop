package com.swiftdrop.pricing.core.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** FR-PRC-004/009: the maximum comes only from the SRS 9.2 environment binding. */
@Validated
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(@NotNull @DecimalMin("0.01") BigDecimal maxParcelWeightKg) { }
