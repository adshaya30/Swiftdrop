package com.swiftdrop.delivery.courier.dto;

import java.time.Instant;

public record DeliveryProofDto(
        String recipientName,
        String note,
        boolean hasPhoto,
        Instant capturedAt) {
}

