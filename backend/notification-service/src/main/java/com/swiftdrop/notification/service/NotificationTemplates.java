package com.swiftdrop.notification.service;

import com.swiftdrop.common.enums.FailureReason;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FR-NTF-003: Notification templates (English only).
 * Every parcel message contains the tracking number and public link.
 * SMS at most 160 characters, no names or addresses.
 * Email bodies are plain text; a greeting with the full name is allowed in emails only.
 */
@Component
public class NotificationTemplates {

    private final String publicBaseUrl;

    public NotificationTemplates(
            @Value("${notification.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public record RenderedNotification(String subject, String body) {}

    /**
     * Render a template with the given parameters.
     * @param templateCode the template identifier
     * @param channel EMAIL or SMS
     * @param params available: trackingNumber, fullName, status, failureReasonCode, reason, businessName
     */
    public RenderedNotification render(String templateCode, String channel, Map<String, String> params) {
        String trackingNumber = params.getOrDefault("trackingNumber", "");
        String link = publicBaseUrl + "/track/" + trackingNumber;
        String fullName = params.getOrDefault("fullName", "Customer");
        String failureReasonCode = params.get("failureReasonCode");
        String reasonLabel = failureLabel(failureReasonCode);
        String reason = params.getOrDefault("reason", "");
        String businessName = params.getOrDefault("businessName", "Merchant");

        boolean isEmail = "EMAIL".equals(channel);

        return switch (templateCode) {
            // Parcel created
            case "parcel_created_sender" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Parcel Created",
                        "Hi " + esc(fullName) + ",\n\nYour parcel " + trackingNumber
                        + " has been created.\nTrack it here: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " created. Track: " + link);

            // Status changes - sender
            case "status_picked_up_sender" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Parcel Picked Up",
                        "Hi " + esc(fullName) + ",\n\nYour parcel " + trackingNumber
                        + " has been picked up.\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " picked up. Track: " + link);

            case "status_in_transit_sender" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Parcel In Transit",
                        "Hi " + esc(fullName) + ",\n\nYour parcel " + trackingNumber
                        + " is in transit.\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " in transit. Track: " + link);

            case "status_out_for_delivery_sender" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Out for Delivery",
                        "Hi " + esc(fullName) + ",\n\nYour parcel " + trackingNumber
                        + " is out for delivery.\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " out for delivery. Track: " + link);

            case "status_delivered_sender" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Parcel Delivered",
                        "Hi " + esc(fullName) + ",\n\nYour parcel " + trackingNumber
                        + " has been delivered.\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " delivered. Track: " + link);

            case "status_failed_sender" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Delivery Failed",
                        "Hi " + esc(fullName) + ",\n\nDelivery of parcel " + trackingNumber
                        + " has failed. Reason: " + reasonLabel
                        + ".\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " failed: " + reasonLabel + ". " + link);

            case "status_cancelled_sender" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Parcel Cancelled",
                        "Hi " + esc(fullName) + ",\n\nYour parcel " + trackingNumber
                        + " has been cancelled.\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " cancelled. Track: " + link);

            // Status changes - recipient
            case "status_picked_up_recipient" -> isEmail
                    ? new RenderedNotification("SwiftDrop: A Parcel Is On Its Way",
                        "Hello,\n\nA parcel (" + trackingNumber + ") has been picked up and is on its way to you."
                        + "\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " picked up, coming to you. " + link);

            case "status_out_for_delivery_recipient" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Parcel Out for Delivery",
                        "Hello,\n\nA parcel (" + trackingNumber + ") is out for delivery to you."
                        + "\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " out for delivery to you. " + link);

            case "status_delivered_recipient" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Parcel Delivered",
                        "Hello,\n\nParcel " + trackingNumber + " has been delivered."
                        + "\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " delivered. " + link);

            case "status_failed_recipient" -> isEmail
                    ? new RenderedNotification("SwiftDrop: Delivery Attempt Failed",
                        "Hello,\n\nDelivery of parcel " + trackingNumber
                        + " has failed. Reason: " + reasonLabel
                        + ".\nTrack: " + link + "\n\nSwiftDrop")
                    : sms("Parcel " + trackingNumber + " delivery failed: " + reasonLabel + ". " + link);

            // Merchant status
            case "merchant_approved" -> new RenderedNotification("SwiftDrop: Merchant Account Approved",
                    "Dear " + esc(businessName) + ",\n\nYour merchant account has been approved."
                    + "\n\nYou can now start using the SwiftDrop API.\n\nSwiftDrop");

            case "merchant_reinstated" -> new RenderedNotification("SwiftDrop: Merchant Account Reinstated",
                    "Dear " + esc(businessName) + ",\n\nYour merchant account has been reinstated."
                    + "\n\nSwiftDrop");

            case "merchant_rejected" -> new RenderedNotification("SwiftDrop: Merchant Application Rejected",
                    "Dear " + esc(businessName) + ",\n\nYour merchant application has been rejected."
                    + "\nReason: " + esc(reason) + "\n\nSwiftDrop");

            case "merchant_suspended" -> new RenderedNotification("SwiftDrop: Merchant Account Suspended",
                    "Dear " + esc(businessName) + ",\n\nYour merchant account has been suspended."
                    + "\nReason: " + esc(reason) + "\n\nSwiftDrop");

            default -> new RenderedNotification("SwiftDrop Notification",
                    "You have a notification regarding parcel " + trackingNumber + ". Track: " + link);
        };
    }

    private RenderedNotification sms(String body) {
        // Truncate to 160 chars (FR-NTF-003)
        String truncated = body.length() > 160 ? body.substring(0, 157) + "..." : body;
        return new RenderedNotification(null, truncated);
    }

    private static String failureLabel(String code) {
        if (code == null || code.isBlank()) return "Unknown reason";
        try {
            return FailureReason.valueOf(code).label();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    /** Escape user-provided text in email bodies (FR-NTF-003). */
    private static String esc(String s) {
        return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;");
    }
}

