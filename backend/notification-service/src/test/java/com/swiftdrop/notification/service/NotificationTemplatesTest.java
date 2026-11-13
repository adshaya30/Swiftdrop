package com.swiftdrop.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTemplatesTest {
    private final NotificationTemplates templates = new NotificationTemplates("https://public.example");
    private static final List<String> PARCEL_TEMPLATES = List.of(
            "parcel_created_sender", "status_picked_up_sender", "status_in_transit_sender",
            "status_out_for_delivery_sender", "status_delivered_sender", "status_failed_sender",
            "status_cancelled_sender", "status_picked_up_recipient", "status_out_for_delivery_recipient",
            "status_delivered_recipient", "status_failed_recipient");

    @Test
    void everyParcelTemplateIncludesTrackingNumberAndPublicUrlAndSmsIsPrivateAndBounded() {
        Map<String, String> params = Map.of("trackingNumber", "SD1234567890", "fullName", "Private Person",
                "failureReasonCode", "RECIPIENT_UNAVAILABLE");
        for (String templateCode : PARCEL_TEMPLATES) {
            var email = templates.render(templateCode, "EMAIL", params);
            assertThat(email.subject()).isNotBlank();
            assertThat(email.body()).contains("SD1234567890", "https://public.example/track/SD1234567890");
            assertThat(email.body()).doesNotContain("<html", "<body");

            var sms = templates.render(templateCode, "SMS", params);
            assertThat(sms.body()).hasSizeLessThanOrEqualTo(160)
                    .contains("SD1234567890", "https://public.example/track/SD1234567890")
                    .doesNotContain("Private Person", "example.test", "+9477");
        }
    }

    @Test
    void merchantTemplatesUseReasonOnlyInPlainTextEmail() {
        var rejected = templates.render("merchant_rejected", "EMAIL",
                Map.of("businessName", "Merchant Ltd", "reason", "Application incomplete"));
        assertThat(rejected.subject()).contains("Rejected");
        assertThat(rejected.body()).contains("Merchant Ltd", "Application incomplete").doesNotContain("<html");
        var suspended = templates.render("merchant_suspended", "EMAIL",
                Map.of("businessName", "Merchant Ltd", "reason", "Review required"));
        assertThat(suspended.body()).contains("Review required");
        assertThat(templates.render("merchant_reinstated", "EMAIL", Map.of()).body()).contains("reinstated");
        assertThat(templates.render("merchant_approved", "EMAIL", Map.of()).body()).contains("approved");
    }
}
