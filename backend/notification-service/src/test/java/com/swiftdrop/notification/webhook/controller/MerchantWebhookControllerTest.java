package com.swiftdrop.notification.webhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.common.security.SecurityAutoConfiguration;
import com.swiftdrop.notification.security.NotificationSecurityRules;
import com.swiftdrop.notification.webhook.dto.request.MerchantWebhookConfigRequest;
import com.swiftdrop.notification.webhook.dto.response.MerchantWebhookConfigResponse;
import com.swiftdrop.notification.webhook.dto.response.RotateSecretResponse;
import com.swiftdrop.notification.webhook.dto.response.WebhookDeliveryItemResponse;
import com.swiftdrop.notification.webhook.dto.response.WebhookSummaryResponse;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.notification.webhook.service.MerchantWebhookService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.swiftdrop.notification.webhook.controller.WebhookTestConfiguration.TOKENS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MerchantWebhookController.class, properties = "INTERNAL_API_KEY=notification-internal-test-key-32-chars")
@Import({NotificationSecurityRules.class, SecurityAutoConfiguration.class, WebhookTestConfiguration.class})
@Tag("TST-032")
@Tag("TST-033")
@Tag("FR-WHK-001")
@Tag("FR-WHK-006")
class MerchantWebhookControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MerchantWebhookService webhookService;

    private static final UUID MERCHANT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("TST-033: Unauthenticated request to /api/v1/merchant/webhook returns 401 UNAUTHENTICATED")
    void getWebhook_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/merchant/webhook"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("TST-033: Customer role caller receives 403 ACCESS_DENIED")
    void getWebhook_customerRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/merchant/webhook")
                        .header("Authorization", TOKENS.customer().bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("TST-033: Courier role caller receives 403 ACCESS_DENIED")
    void getWebhook_courierRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/merchant/webhook")
                        .header("Authorization", TOKENS.courier().bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("TST-033: Admin role caller receives 403 ACCESS_DENIED")
    void getWebhook_adminRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/merchant/webhook")
                        .header("Authorization", TOKENS.admin().bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("TST-032: Client token (typ=client) receives 403 ACCESS_DENIED on webhook endpoints")
    void getWebhook_clientToken_returns403() throws Exception {
        mvc.perform(get("/api/v1/merchant/webhook")
                        .header("Authorization", TOKENS.client(MERCHANT_ID, "orders:read").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("TST-033: Password change required token receives 403 PASSWORD_CHANGE_REQUIRED")
    void getWebhook_passwordChangeRequired_returns403() throws Exception {
        mvc.perform(get("/api/v1/merchant/webhook")
                        .header("Authorization", TOKENS.merchant(MERCHANT_ID).passwordChangeRequired(true).bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    @DisplayName("FR-WHK-001: Authenticated merchant can get webhook config")
    void getWebhook_merchant_returns200() throws Exception {
        when(webhookService.getConfig(MERCHANT_ID))
                .thenReturn(MerchantWebhookConfigResponse.withoutSecret("https://merchant.example/webhook", true, true));

        mvc.perform(get("/api/v1/merchant/webhook")
                        .header("Authorization", TOKENS.merchant(MERCHANT_ID).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://merchant.example/webhook"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.hasSecret").value(true))
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    @DisplayName("FR-WHK-001: Authenticated merchant can save webhook config")
    void saveWebhook_merchant_returns200() throws Exception {
        MerchantWebhookConfigRequest request = new MerchantWebhookConfigRequest("https://merchant.example/webhook", true);
        when(webhookService.saveConfig(eq(MERCHANT_ID), any()))
                .thenReturn(MerchantWebhookConfigResponse.withSecret("https://merchant.example/webhook", true, true, "whsec_SECRET"));

        mvc.perform(put("/api/v1/merchant/webhook")
                        .header("Authorization", TOKENS.merchant(MERCHANT_ID).bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://merchant.example/webhook"))
                .andExpect(jsonPath("$.secret").value("whsec_SECRET"));
    }

    @Test
    @DisplayName("FR-WHK-003: Authenticated merchant can rotate secret")
    void rotateSecret_merchant_returns200() throws Exception {
        when(webhookService.rotateSecret(MERCHANT_ID))
                .thenReturn(new RotateSecretResponse("whsec_NEW_SECRET"));

        mvc.perform(post("/api/v1/merchant/webhook/rotate-secret")
                        .header("Authorization", TOKENS.merchant(MERCHANT_ID).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("whsec_NEW_SECRET"));
    }

    @Test
    @DisplayName("FR-WHK-009: Authenticated merchant can send test ping")
    void testWebhook_merchant_returns200() throws Exception {
        WebhookDeliveryItemResponse item = new WebhookDeliveryItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), "webhook.test", null,
                "https://merchant.example", WebhookDeliveryStatus.PENDING, 0,
                Instant.now(), null, null, Instant.now(), null, null
        );
        when(webhookService.triggerTest(MERCHANT_ID)).thenReturn(item);

        mvc.perform(post("/api/v1/merchant/webhook/test")
                        .header("Authorization", TOKENS.merchant(MERCHANT_ID).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("webhook.test"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("FR-WHK-004: Authenticated merchant can list webhook deliveries with pagination")
    void listDeliveries_merchant_returns200() throws Exception {
        WebhookDeliveryItemResponse item = new WebhookDeliveryItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), "parcel.status.changed", "SD-1234567890",
                "https://merchant.example", WebhookDeliveryStatus.SUCCEEDED, 1,
                null, 200, null, Instant.now(), Instant.now(), Instant.now()
        );
        when(webhookService.listDeliveries(eq(MERCHANT_ID), any(), any()))
                .thenReturn(Pagination.of(List.of(item), 0, 20, 1));

        mvc.perform(get("/api/v1/merchant/webhook/deliveries")
                        .header("Authorization", TOKENS.merchant(MERCHANT_ID).bearer())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD-1234567890"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("FR-WHK-004: Authenticated merchant can retry failed delivery")
    void retryDelivery_merchant_returns200() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        WebhookDeliveryItemResponse item = new WebhookDeliveryItemResponse(
                deliveryId, UUID.randomUUID(), "parcel.status.changed", "SD-1234567890",
                "https://merchant.example", WebhookDeliveryStatus.PENDING, 0,
                Instant.now(), null, null, Instant.now(), null, null
        );
        when(webhookService.retryDelivery(MERCHANT_ID, deliveryId)).thenReturn(item);

        mvc.perform(post("/api/v1/merchant/webhook/deliveries/{id}/retry", deliveryId)
                        .header("Authorization", TOKENS.merchant(MERCHANT_ID).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("FR-WHK-006: Authenticated merchant can get summary counters")
    void getSummary_merchant_returns200() throws Exception {
        when(webhookService.getSummary(MERCHANT_ID))
                .thenReturn(new WebhookSummaryResponse(2L, 1L, 10L));

        mvc.perform(get("/api/v1/merchant/webhook/summary")
                        .header("Authorization", TOKENS.merchant(MERCHANT_ID).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed24h").value(2))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.succeeded24h").value(10));
    }
}

