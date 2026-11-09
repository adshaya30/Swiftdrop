package com.swiftdrop.common.enums;

/** SRS 6.2 parcel statuses with display labels; Table 2.3: DELIVERED, FAILED and CANCELLED are terminal. */
public enum ParcelStatus {
    CREATED("Created"),
    PICKED_UP("Picked up"),
    IN_TRANSIT("In transit"),
    OUT_FOR_DELIVERY("Out for delivery"),
    DELIVERED("Delivered"),
    FAILED("Delivery failed"),
    CANCELLED("Cancelled");

    private final String label;

    ParcelStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED || this == CANCELLED;
    }
}
