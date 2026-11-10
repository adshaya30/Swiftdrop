package com.swiftdrop.pricing.core.dto.response;

import com.swiftdrop.common.enums.ParcelSize;
import java.math.BigDecimal;

public record SizeOptionDto(ParcelSize size, BigDecimal surcharge, String description) { }
