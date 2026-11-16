package com.swiftdrop.parcel.order.dto;

/**
 * SRS 7.4 / FR-NTF-007: {name, phone, email} of the drop-off contact, fetched by notification-service at send time.
 * Personal data: never log it (BR-28).
 */
public record RecipientContact(String name, String phone, String email) {

    @Override
    public String toString() {
        return "RecipientContact[masked]";
    }
}
