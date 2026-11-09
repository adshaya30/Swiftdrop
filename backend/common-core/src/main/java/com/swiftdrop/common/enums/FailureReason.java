package com.swiftdrop.common.enums;

import java.util.EnumSet;
import java.util.Set;

/** SRS 6.2 failure reasons with labels; Table 2.4 decides which reasons are allowed per stage. */
public enum FailureReason {
    RECIPIENT_UNAVAILABLE("Recipient not available"),
    WRONG_OR_INCOMPLETE_ADDRESS("Wrong or incomplete address"),
    RECIPIENT_REFUSED("Recipient refused the parcel"),
    SENDER_UNAVAILABLE_AT_PICKUP("Sender not available at pickup"),
    PARCEL_DAMAGED("Parcel damaged"),
    ACCESS_RESTRICTED("Location not accessible"),
    OTHER("Other reason");

    private final String label;

    FailureReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Table 2.4: the reasons allowed when a parcel in the given status before FAILED is failed.
     * With adminOverride (FR-PAR-012) only PICKED_UP, IN_TRANSIT and OUT_FOR_DELIVERY are allowed.
     * Returns an empty set for any other combination (the caller answers 400 VALIDATION_ERROR on failureReason).
     */
    public static Set<FailureReason> allowedFor(ParcelStatus before, boolean adminOverride) {
        if (before == null) {
            return EnumSet.noneOf(FailureReason.class);
        }
        if (adminOverride) {
            return switch (before) {
                case PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY -> EnumSet.of(PARCEL_DAMAGED, OTHER);
                default -> EnumSet.noneOf(FailureReason.class);
            };
        }
        return switch (before) {
            case CREATED -> EnumSet.of(SENDER_UNAVAILABLE_AT_PICKUP, WRONG_OR_INCOMPLETE_ADDRESS, ACCESS_RESTRICTED, OTHER);
            case PICKED_UP, IN_TRANSIT -> EnumSet.of(PARCEL_DAMAGED, OTHER);
            case OUT_FOR_DELIVERY -> EnumSet.of(RECIPIENT_UNAVAILABLE, WRONG_OR_INCOMPLETE_ADDRESS, RECIPIENT_REFUSED,
                    PARCEL_DAMAGED, ACCESS_RESTRICTED, OTHER);
            default -> EnumSet.noneOf(FailureReason.class);
        };
    }
}
