package com.swiftdrop.pricing.admin.dto;

import com.swiftdrop.common.enums.ParcelSize;
import java.math.BigDecimal;

/** FR-PRC-007: {size, surcharge, description}. */
public record SizeSurchargeResponse(ParcelSize size, BigDecimal surcharge, String description) { }
