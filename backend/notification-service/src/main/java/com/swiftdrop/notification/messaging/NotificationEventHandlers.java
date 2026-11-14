package com.swiftdrop.notification.messaging;

import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.service.NotificationDeliveryService;
import com.swiftdrop.notification.service.NotificationPlanner;
import com.swiftdrop.notification.service.NotificationPlanningService;
import com.swiftdrop.notification.webhook.service.WebhookEnqueueService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles planning and delivery before EventConsumerSupport acknowledges the RabbitMQ event. */
@Component
public class NotificationEventHandlers {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandlers.class);
    private final NotificationPlanner planner;
    private final NotificationPlanningService planning;
    private final NotificationDeliveryService delivery;
    private final WebhookEnqueueService webhookEnqueue;

    public NotificationEventHandlers(NotificationPlanner planner, NotificationPlanningService planning,
            NotificationDeliveryService delivery, WebhookEnqueueService webhookEnqueue) {
        this.planner = planner;
        this.planning = planning;
        this.delivery = delivery;
        this.webhookEnqueue = webhookEnqueue;
    }

    public void handle(EventEnvelope<?> envelope) {
        List<NotificationPlanner.PlannedNotification> planned;
        String trackingNumber = null;
        switch (envelope.eventType()) {
            case EventTypes.PARCEL_CREATED -> {
                ParcelCreatedData data = (ParcelCreatedData) envelope.data();
                trackingNumber = data.trackingNumber();
                planned = planner.planParcelCreated(data);
            }
            case EventTypes.PARCEL_STATUS_CHANGED -> {
                var data = (ParcelStatusChangedData) envelope.data();
                trackingNumber = data.trackingNumber();
                planned = planner.planStatusChanged(data);
            }
            case EventTypes.MERCHANT_STATUS_CHANGED ->
                    planned = planner.planMerchantStatusChanged((MerchantStatusChangedData) envelope.data());
            default -> {
                log.warn("Unexpected notification event type {}", envelope.eventType());
                return;
            }
        }

        List<Notification> rows = planning.plan(envelope, trackingNumber, planned);
        delivery.deliver(envelope, rows);
        if (EventTypes.PARCEL_STATUS_CHANGED.equals(envelope.eventType())) {
            @SuppressWarnings("unchecked")
            EventEnvelope<ParcelStatusChangedData> statusEvent = (EventEnvelope<ParcelStatusChangedData>) envelope;
            webhookEnqueue.enqueueStatusChanged(statusEvent);
        }
    }
}
