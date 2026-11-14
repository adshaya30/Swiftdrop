package com.swiftdrop.notification.service;

import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.common.util.MaskingUtil;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.service.NotificationContactResolver.Contact;
import com.swiftdrop.notification.service.NotificationStateService.Snapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Synchronously delivers the event's rows before the RabbitMQ handler returns and permits acknowledgement. */
@Service
public class NotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private final NotificationContactResolver contacts;
    private final NotificationTemplates templates;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final NotificationStateService state;
    private final NotificationLockService locks;

    public NotificationDeliveryService(NotificationContactResolver contacts, NotificationTemplates templates,
            EmailSender emailSender, SmsSender smsSender, NotificationStateService state,
            NotificationLockService locks) {
        this.contacts = contacts;
        this.templates = templates;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.state = state;
        this.locks = locks;
    }

    public void deliver(EventEnvelope<?> event, List<Notification> rows) {
        NotificationProcessingException failure = null;
        Map<String, Contact> resolved = new HashMap<>();
        for (Notification row : rows) {
            try {
                deliverOne(event, row, resolved);
            } catch (NotificationProcessingException ex) {
                // Attempt every channel for the event, then let shared consumer retry and DLQ the event.
                failure = ex;
            }
        }
        if (failure != null) throw failure;
    }

    private void deliverOne(EventEnvelope<?> event, Notification row, Map<String, Contact> resolved) {
        NotificationLockService.Lock lock = locks.acquire(row.getId());
        try (lock) {
            Snapshot snapshot = state.load(row.getId());
            if ("SENT".equals(snapshot.status()) || "SKIPPED".equals(snapshot.status())) return;

            String masked = null;
            try {
                Contact contact = resolved.computeIfAbsent(snapshot.recipientKind(), kind -> contacts.resolve(event, row));
                String channel = snapshot.channel();
                String destination = "EMAIL".equals(channel) ? contact.email() : contact.phone();
                masked = "EMAIL".equals(channel)
                        ? MaskingUtil.maskEmail(destination) : MaskingUtil.maskPhone(destination);
                if (destination == null || destination.isBlank()) {
                    state.skipped(snapshot.id(), masked);
                    log.debug("Notification {} skipped because its destination is unavailable", snapshot.id());
                    return;
                }
                Map<String, String> params = templateParams(event, contact);
                NotificationTemplates.RenderedNotification rendered =
                        templates.render(snapshot.templateCode(), channel, params);
                if ("EMAIL".equals(channel)) {
                    emailSender.send(destination, rendered.subject(), rendered.body());
                    state.sent(snapshot.id(), snapshot.attempts() + 1, masked, null);
                } else if ("SMS".equals(channel)) {
                    smsSender.send(destination, rendered.body());
                    state.sent(snapshot.id(), snapshot.attempts() + 1, masked, rendered.body());
                } else {
                    throw new NotificationProcessingException();
                }
                log.info("Notification {} delivered via {} to {}", snapshot.id(), channel, masked);
            } catch (NotificationProcessingException ex) {
                safelyMarkFailed(snapshot, masked);
                throw ex;
            } catch (Exception ex) {
                // Do not retain exception text or cause: SMTP/client messages can contain raw destinations.
                safelyMarkFailed(snapshot, masked);
                throw new NotificationProcessingException();
            }
        }
    }

    private void safelyMarkFailed(Snapshot snapshot, String masked) {
        state.failed(snapshot.id(), snapshot.attempts() + 1, masked);
    }

    private static Map<String, String> templateParams(EventEnvelope<?> event, Contact contact) {
        Map<String, String> params = new HashMap<>();
        if (EventTypes.PARCEL_STATUS_CHANGED.equals(event.eventType())) {
            ParcelStatusChangedData data = (ParcelStatusChangedData) event.data();
            params.put("trackingNumber", data.trackingNumber());
            if (data.failureReasonCode() != null) params.put("failureReasonCode", data.failureReasonCode().name());
        } else if (EventTypes.PARCEL_CREATED.equals(event.eventType())) {
            var data = (com.swiftdrop.common.events.data.ParcelCreatedData) event.data();
            params.put("trackingNumber", data.trackingNumber());
        } else if (EventTypes.MERCHANT_STATUS_CHANGED.equals(event.eventType())) {
            MerchantStatusChangedData data = (MerchantStatusChangedData) event.data();
            if (data.businessName() != null) params.put("businessName", data.businessName());
            if (data.reason() != null) params.put("reason", data.reason());
        }
        if (contact.fullName() != null) params.put("fullName", contact.fullName());
        if (contact.businessName() != null) params.put("businessName", contact.businessName());
        return params;
    }
}
