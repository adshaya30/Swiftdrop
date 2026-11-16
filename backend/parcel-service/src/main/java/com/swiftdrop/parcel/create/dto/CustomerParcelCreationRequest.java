package com.swiftdrop.parcel.create.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** FR-PAR-005: customer wire body; identity comes from the authenticated user, not JSON. */
public record CustomerParcelCreationRequest(
        @NotNull @Valid ParcelInput.Pickup pickup,
        @NotNull @Valid ParcelInput.Dropoff dropoff,
        @NotNull @Valid ParcelInput.ParcelData parcel,
        @Size(max = 300) String deliveryInstructions,
        @NotNull BigDecimal expectedFee) {
    public ParcelInput toParcelInput() {
        return new ParcelInput(pickup, dropoff, parcel, deliveryInstructions);
    }
}
