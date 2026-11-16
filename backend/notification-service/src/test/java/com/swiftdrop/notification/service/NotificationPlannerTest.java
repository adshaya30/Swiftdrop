package com.swiftdrop.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPlannerTest {
    private final NotificationPlanner planner = new NotificationPlanner();

    @Test
    void plansSenderNotificationsForCustomerParcelStatusMatrix() {
        for (ParcelStatus status : new ParcelStatus[] {ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT,
                ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, ParcelStatus.FAILED, ParcelStatus.CANCELLED}) {
            var planned = planner.planStatusChanged(event(OwnerType.CUSTOMER, ParcelStatus.CREATED, status));
            assertThat(planned).filteredOn(item -> item.recipientKind().name().equals("SENDER"))
                    .extracting(item -> item.channel().name()).containsExactly("EMAIL", "SMS");
        }
    }

    @Test
    void recipientMatrixOnlyNotifiesAtSupportedStatusesAndOodFailure() {
        for (ParcelStatus status : new ParcelStatus[] {ParcelStatus.PICKED_UP, ParcelStatus.OUT_FOR_DELIVERY,
                ParcelStatus.DELIVERED}) {
            var planned = planner.planStatusChanged(event(OwnerType.MERCHANT, ParcelStatus.CREATED, status));
            assertThat(planned).filteredOn(item -> item.recipientKind().name().equals("RECIPIENT"))
                    .extracting(item -> item.channel().name()).containsExactly("SMS", "EMAIL");
        }
        assertThat(planner.planStatusChanged(event(OwnerType.MERCHANT, ParcelStatus.PICKED_UP, ParcelStatus.FAILED)))
                .isEmpty();
        assertThat(planner.planStatusChanged(event(OwnerType.MERCHANT, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED)))
                .extracting(item -> item.templateCode()).containsExactly("status_failed_recipient", "status_failed_recipient");
    }

    @Test
    void merchantOwnedParcelsNeverPlanSenderMessages() {
        var planned = planner.planStatusChanged(event(OwnerType.MERCHANT, ParcelStatus.IN_TRANSIT,
                ParcelStatus.OUT_FOR_DELIVERY));
        assertThat(planned).noneMatch(item -> item.recipientKind().name().equals("SENDER"));
    }

    private static ParcelStatusChangedData event(OwnerType owner, ParcelStatus previous, ParcelStatus current) {
        return new ParcelStatusChangedData(UUID.randomUUID(), "SD1234567890", 2, previous, current, Instant.now(),
                null, null, null, null, owner, UUID.randomUUID(), null, null);
    }
}
