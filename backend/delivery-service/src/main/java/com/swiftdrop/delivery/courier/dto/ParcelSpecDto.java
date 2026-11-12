package com.swiftdrop.delivery.courier.dto;

import com.swiftdrop.common.enums.ParcelSize;
import java.math.BigDecimal;

public record ParcelSpecDto(
        BigDecimal weightKg,
        ParcelSize size,
        String description) {
}

