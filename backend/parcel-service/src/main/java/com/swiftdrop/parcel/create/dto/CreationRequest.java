package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.OwnerType;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Master 8.1 contract for both callers. Customer ownerId = sub; merchant ownerId = mid.
 * Input must already be validated and requestHash must cover the complete validated wire
 * body, including expectedFee and merchantReference when present. Nullable: expectedFee
 * (merchant only), merchantReference, idempotencyKey.
 * The future implementation checks idempotency before invoking the discount resolver.
 */
public record CreationRequest(
        OwnerType ownerType, UUID ownerId, ParcelInput input, BigDecimal expectedFee,
        String merchantReference, String idempotencyKey, String requestHash,
        Supplier<BigDecimal> discountPercentResolver,
        Function<CreatedParcel, Object> responseBuilder) { }
