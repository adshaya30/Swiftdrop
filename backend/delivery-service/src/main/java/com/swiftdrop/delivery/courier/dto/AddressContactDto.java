package com.swiftdrop.delivery.courier.dto;

public record AddressContactDto(
        String contactName,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String city,
        String districtCode,
        String districtName,
        String postalCode,
        String contactEmail) {
}

