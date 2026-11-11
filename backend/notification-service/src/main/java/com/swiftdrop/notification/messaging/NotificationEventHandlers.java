package com.swiftdrop.notification.messaging;

import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.service.NotificationPlanner;
import com.swiftdrop.notification.service.NotificationPlanner.PlannedNotification;
import com.swiftdrop.notification.service.NotificationTemplates;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional event handlers for notification planning (FR-NTF-001).
 */
@Component
public class NotificationEventHandlers {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandlers.class);

    private final NotificationRepository notificationRepo;
    private final NotificationPlanner planner;
    private final NotificationTemplates templates;

    public NotificationEventHandlers(NotificationRepository notificationRepo,
                                     NotificationPlanner planner,
                                     NotificationTemplates templates) {
        this.notificationRepo = notificationRepo;
        this.planner = planner;
        this.templates = templates;
    }

    @Transactional
    public void handle(EventEnvelope<?> envelope) {
        switch (envelope.eventType()) {
            case EventTypes.PARCEL_CREATED -> handleParcelCreated(envelope);
            case EventTypes.PARCEL_STATUS_CHANGED -> handleStatusChanged(envelope);
            case EventTypes.MERCHANT_STATUS_CHANGED -> handleMerchantStatusChanged(envelope);
            default -> log.warn("Unexpected event type: {}", envelope.eventType());
        }
    }

    private void handleParcelCreated(EventEnvelope<?> envelope) {
        var data = (ParcelCreatedData) envelope.data();
        savePlanned(envelope, data.trackingNumber(), planner.planParcelCreated(data),
                Map.of("trackingNumber", data.trackingNumber()));
    }

    private void handleStatusChanged(EventEnvelope<?> envelope) {
        var data = (ParcelStatusChangedData) envelope.data();
        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", data.trackingNumber());
        if (data.failureReasonCode() != null) {
            params.put("failureReasonCode", data.failureReasonCode().name());
        }
        savePlanned(envelope, data.trackingNumber(), planner.planStatusChanged(data), params);
    }

    private void handleMerchantStatusChanged(EventEnvelope<?> envelope) {
        var data = (MerchantStatusChangedData) envelope.data();
        Map<String, String> params = new HashMap<>();
        if (data.businessName() != null) {
            params.put("businessName", data.businessName());
        }
        if (data.reason() != null) {
            params.put("reason", data.reason());
        }
        savePlanned(envelope, null, planner.planMerchantStatusChanged(data), params);
    }

    private void savePlanned(EventEnvelope<?> envelope, String trackingNumber,
                             Iterable<PlannedNotification> plannedNotifications,
                             Map<String, String> params) {
        for (PlannedNotification planned : plannedNotifications) {
            String channel = planned.channel().name();
            String recipientKind = planned.recipientKind().name();
            if (notificationRepo.findByEventIdAndChannelAndRecipientKind(
                    envelope.eventId(), channel, recipientKind).isPresent()) {
                log.debug("Duplicate notification for event {} ({}, {}) ignored",
                        envelope.eventId(), channel, recipientKind);
                continue;
            }

            var rendered = templates.render(planned.templateCode(), channel, params);
            Notification notification = new Notification();
            notification.setEventId(envelope.eventId());
            notification.setEventType(envelope.eventType());
            notification.setTrackingNumber(trackingNumber);
            notification.setChannel(channel);
            notification.setRecipientKind(recipientKind);
            notification.setTemplateCode(planned.templateCode());
            notification.setBodyPreview(rendered.body());
            notificationRepo.save(notification);
        }
    }
}
