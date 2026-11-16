package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.common.enums.ParcelStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** FR-PAR-009: owner detail snapshot; history, courier and proof remain in their owning services. */
public record ParcelDetailDto(UUID id, String trackingNumber, ParcelStatus status, OwnerType ownerType,
        String merchantReference, Pickup pickup, Dropoff dropoff, ParcelData parcel,
        String deliveryInstructions, Zone originZone, Zone destinationZone,
        CreatedParcel.Fee fee, Instant createdAt, Instant updatedAt) {
    public record Pickup(String contactName, String contactPhone, String addressLine1, String addressLine2,
                         String city, String districtCode, String districtName, String postalCode) { }
    public record Dropoff(String contactName, String contactPhone, String addressLine1, String addressLine2,
                          String city, String districtCode, String districtName, String postalCode,
                          String contactEmail) { }
    public record ParcelData(BigDecimal weightKg, ParcelSize size, String description) { }
    public record Zone(String code, String name) { }
}
