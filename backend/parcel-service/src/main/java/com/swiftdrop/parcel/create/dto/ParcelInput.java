package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.common.util.PhoneNumberNormalizer;
import com.swiftdrop.parcel.create.validation.ValidPhone;
import com.swiftdrop.parcel.create.validation.ValidWeight;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** FR-PAR-005: validated creation data shared by customer and merchant callers. */
public record ParcelInput(
        @NotNull @Valid Pickup pickup,
        @NotNull @Valid Dropoff dropoff,
        @NotNull @Valid ParcelData parcel,
        @Size(max = 300) String deliveryInstructions) {
    public record Pickup(
            @NotBlank @Size(min = 2, max = 100) String contactName,
            @NotNull @ValidPhone String contactPhone,
            @NotBlank @Size(max = 150) String addressLine1,
            @Size(max = 150) String addressLine2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 20) String districtCode,
            @Pattern(regexp = "[0-9]{5}") String postalCode) {
        public Pickup {
            contactPhone = PhoneNumberNormalizer.normalize(contactPhone).orElse(contactPhone);
        }
    }
    public record Dropoff(
            @NotBlank @Size(min = 2, max = 100) String contactName,
            @NotNull @ValidPhone String contactPhone,
            @NotBlank @Size(max = 150) String addressLine1,
            @Size(max = 150) String addressLine2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 20) String districtCode,
            @Pattern(regexp = "[0-9]{5}") String postalCode,
            @Email @Size(max = 254) String contactEmail) {
        public Dropoff {
            contactPhone = PhoneNumberNormalizer.normalize(contactPhone).orElse(contactPhone);
        }
    }
    public record ParcelData(
            @NotNull @ValidWeight BigDecimal weightKg,
            @NotNull ParcelSize size,
            @Size(max = 200) String description) { }
}
