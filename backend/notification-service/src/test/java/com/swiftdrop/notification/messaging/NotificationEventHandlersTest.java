package com.swiftdrop.notification.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.notification.service.NotificationDeliveryService;
import com.swiftdrop.notification.service.NotificationPlanner;
import com.swiftdrop.notification.service.NotificationPlanningService;
import com.swiftdrop.notification.webhook.service.WebhookEnqueueService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationEventHandlersTest {
    private final NotificationPlanningService planning = mock(NotificationPlanningService.class);
    private final NotificationDeliveryService delivery = mock(NotificationDeliveryService.class);
    private final WebhookEnqueueService webhooks = mock(WebhookEnqueueService.class);
    private final NotificationEventHandlers handlers = new NotificationEventHandlers(
            new NotificationPlanner(), planning, delivery, webhooks);

    @Test
    void parcelCreatedPlansAndDeliversSenderEmailAndSms() {
        var data = new ParcelCreatedData(UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED,
                OwnerType.CUSTOMER, UUID.randomUUID(), null, null, "D1", "District", "Zone", "D2",
                "Destination", "Zone", "Pickup", "Dropoff", Instant.now());
        var envelope = EventEnvelope.create(EventTypes.PARCEL_CREATED, Instant.now(), UUID.randomUUID(), data);
        when(planning.plan(eq(envelope), eq(data.trackingNumber()), any())).thenReturn(List.of());

        handlers.handle(envelope);

        verify(planning).plan(eq(envelope), eq(data.trackingNumber()), any());
        verify(delivery).deliver(envelope, List.of());
    }
}
