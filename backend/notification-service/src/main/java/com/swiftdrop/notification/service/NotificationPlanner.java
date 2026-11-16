package com.swiftdrop.notification.service;

import com.swiftdrop.common.enums.*;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * FR-NTF-001, Table 2.5: Pure function deciding who gets what notification.
 * No side effects; no I/O.
 */
@Component
public class NotificationPlanner {

    /** Statuses that trigger sender notifications for customer-owned parcels. */
    private static final Set<ParcelStatus> SENDER_STATUSES = Set.of(
            ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT,
            ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED,
            ParcelStatus.FAILED, ParcelStatus.CANCELLED);

    /** Statuses that trigger recipient notifications. */
    private static final Set<ParcelStatus> RECIPIENT_STATUSES = Set.of(
            ParcelStatus.PICKED_UP, ParcelStatus.OUT_FOR_DELIVERY,
            ParcelStatus.DELIVERED);

    public record PlannedNotification(
            NotificationChannel channel,
            RecipientKind recipientKind,
            String templateCode) {}

    /**
     * Plan notifications for parcel.created.
     * Only customer-owned parcels get sender EMAIL + SMS.
     */
    public List<PlannedNotification> planParcelCreated(ParcelCreatedData data) {
        List<PlannedNotification> result = new ArrayList<>();
        if (data.ownerType() == OwnerType.CUSTOMER) {
            result.add(new PlannedNotification(NotificationChannel.EMAIL, RecipientKind.SENDER,
                    "parcel_created_sender"));
            result.add(new PlannedNotification(NotificationChannel.SMS, RecipientKind.SENDER,
                    "parcel_created_sender"));
        }
        return result;
    }

    /**
     * Plan notifications for parcel.status.changed (Table 2.5).
     */
    public List<PlannedNotification> planStatusChanged(ParcelStatusChangedData data) {
        List<PlannedNotification> result = new ArrayList<>();
        ParcelStatus status = data.status();
        ParcelStatus previousStatus = data.previousStatus();

        // Sender notifications: customer-owned parcels only
        if (data.ownerType() == OwnerType.CUSTOMER && SENDER_STATUSES.contains(status)) {
            String template = "status_" + status.name().toLowerCase() + "_sender";
            result.add(new PlannedNotification(NotificationChannel.EMAIL, RecipientKind.SENDER, template));
            result.add(new PlannedNotification(NotificationChannel.SMS, RecipientKind.SENDER, template));
        }

        // Recipient notifications (both customer and merchant owned parcels)
        if (RECIPIENT_STATUSES.contains(status)) {
            String template = "status_" + status.name().toLowerCase() + "_recipient";
            // SMS always (drop-off phone is mandatory)
            result.add(new PlannedNotification(NotificationChannel.SMS, RecipientKind.RECIPIENT, template));
            // EMAIL if recipient email was given (caller checks availability)
            result.add(new PlannedNotification(NotificationChannel.EMAIL, RecipientKind.RECIPIENT, template));
        }

        // FAILED notification to recipient ONLY when previousStatus was OUT_FOR_DELIVERY
        if (status == ParcelStatus.FAILED && previousStatus == ParcelStatus.OUT_FOR_DELIVERY) {
            result.add(new PlannedNotification(NotificationChannel.SMS, RecipientKind.RECIPIENT,
                    "status_failed_recipient"));
            result.add(new PlannedNotification(NotificationChannel.EMAIL, RecipientKind.RECIPIENT,
                    "status_failed_recipient"));
        }

        // Merchant webhook: handled separately by WebhookEnqueueService (#10)
        // We signal it via a flag but don't plan a notification row for it.

        return result;
    }

    /**
     * Plan notifications for merchant.status.changed.
     */
    public List<PlannedNotification> planMerchantStatusChanged(MerchantStatusChangedData data) {
        List<PlannedNotification> result = new ArrayList<>();
        String template = switch (data.status()) {
            case APPROVED -> data.previousStatus() == MerchantStatus.SUSPENDED
                    ? "merchant_reinstated" : "merchant_approved";
            case REJECTED -> "merchant_rejected";
            case SUSPENDED -> "merchant_suspended";
            default -> null;
        };
        if (template != null) {
            result.add(new PlannedNotification(NotificationChannel.EMAIL, RecipientKind.MERCHANT, template));
        }
        return result;
    }
}

