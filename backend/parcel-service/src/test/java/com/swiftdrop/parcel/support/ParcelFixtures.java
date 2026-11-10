package com.swiftdrop.parcel.support;

import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.parcel.domain.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Synthetic data only. No parcel creation business service is implemented in PR A. */
public final class ParcelFixtures {
    private ParcelFixtures() { }

    public static Parcel parcel(String trackingNumber, UUID customerId, UUID merchantId, String reference) {
        return new Parcel(trackingNumber, customerId, merchantId, reference,
                new PickupDetails("Test sender", "+94770000001", "Test pickup", null, "Colombo", "COLOMBO", "Colombo", null),
                new DropoffDetails("Test recipient", "+94770000002", "Test dropoff", null, "Kandy", "KANDY", "Kandy", "20000", "test@example.invalid"),
                new BigDecimal("2.00"), ParcelSize.MEDIUM, null, null,
                new ZoneSnapshot("WESTERN", "Western"), new ZoneSnapshot("CENTRAL", "Central"),
                new BigDecimal("500.00"), new BigDecimal("150.00"), new BigDecimal("150.00"),
                new BigDecimal("0.00"), new BigDecimal("800.00"), "LKR");
    }
}
