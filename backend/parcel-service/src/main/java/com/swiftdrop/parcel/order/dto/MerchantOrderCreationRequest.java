package com.swiftdrop.parcel.order.dto;

import com.swiftdrop.parcel.create.dto.ParcelInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * FR-PAR-007 merchant order body: FR-PAR-005 plus an optional merchantReference (up to 64). expectedFee is optional
 * and is compared only when present. The owner comes from the token's mid claim, never from JSON.
 */
public record MerchantOrderCreationRequest(
        @Size(min = 1, max = 64) String merchantReference,
        @NotNull @Valid ParcelInput.Pickup pickup,
        @NotNull @Valid ParcelInput.Dropoff dropoff,
        @NotNull @Valid ParcelInput.ParcelData parcel,
        @Size(max = 300) String deliveryInstructions,
        BigDecimal expectedFee) {

    public ParcelInput toParcelInput() {
        return new ParcelInput(pickup, dropoff, parcel, deliveryInstructions);
    }
}
