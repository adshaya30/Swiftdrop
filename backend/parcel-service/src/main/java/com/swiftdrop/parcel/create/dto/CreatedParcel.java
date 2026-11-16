package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable response-builder view shared with Member #6; never exposes a JPA entity.
 * Merchant callers derive trackingUrl from trackingNumber and PUBLIC_BASE_URL.
 */
public record CreatedParcel(
        UUID id, String trackingNumber, ParcelStatus status, String merchantReference,
        Fee fee, Instant createdAt) {
    public record Fee(String currency, BigDecimal baseFee, BigDecimal weightFee,
                      BigDecimal sizeSurcharge, BigDecimal discountAmount, BigDecimal total) { }
}
