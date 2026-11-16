package com.swiftdrop.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.common.client.InternalRestClientFactory;
import com.swiftdrop.common.enums.MerchantStatus;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.security.InternalApiKey;
import com.swiftdrop.notification.entity.Notification;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationContactResolverTest {
    private static final String KEY = "test-internal-api-key-12345678901234567890";
    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastKey = new AtomicReference<>();
    private final AtomicBoolean failRequests = new AtomicBoolean();
    private int port;
    private NotificationContactResolver resolver;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Key"));
            String body;
            int status = failRequests.get() ? 500 : 200;
            if (lastPath.get().contains("/contact")) {
                body = "{\"id\":\"" + UUID.randomUUID() + "\",\"fullName\":\"Example Person\",\"email\":\"person@example.test\",\"phone\":\"+94771234567\"}";
            } else if (lastPath.get().endsWith("/recipient")) {
                body = "{\"name\":\"Dropoff Person\",\"phone\":\"+94771234567\",\"email\":\"dropoff@example.test\"}";
            } else {
                body = "{\"id\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID()
                        + "\",\"businessName\":\"Merchant Ltd\",\"status\":\"APPROVED\",\"userStatus\":\"ACTIVE\",\"discountPercent\":0,\"contactEmail\":\"merchant@example.test\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
        var factory = new InternalRestClientFactory(new InternalApiKey(KEY), new ObjectMapper());
        resolver = new NotificationContactResolver(factory, "http://127.0.0.1:" + port,
                "http://127.0.0.1:" + port);
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void resolvesCustomerContactUsingExactAuthContractAndInternalKey() {
        UUID customerId = UUID.randomUUID();
        EventEnvelope<?> event = EventEnvelope.create(EventTypes.PARCEL_CREATED, Instant.now(), UUID.randomUUID(),
                new ParcelCreatedData(UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED, OwnerType.CUSTOMER,
                        customerId, null, null, "D1", "D", "Z", "D2", "D", "Z", "P", "D", Instant.now()));
        NotificationContactResolver.Contact result = resolver.resolve(event, notification("SENDER"));
        assertEquals("/internal/v1/users/" + customerId + "/contact", lastPath.get());
        assertEquals(KEY, lastKey.get());
        assertEquals("person@example.test", result.email());
        assertEquals("+94771234567", result.phone());
        assertEquals("Example Person", result.fullName());
        assertEquals("Contact[masked]", result.toString());
    }

    @Test
    void resolvesRecipientAndMerchantUsingExactContracts() {
        UUID parcelId = UUID.randomUUID();
        EventEnvelope<?> parcelEvent = EventEnvelope.create(EventTypes.PARCEL_CREATED, Instant.now(), UUID.randomUUID(),
                new ParcelCreatedData(parcelId, "SD1234567890", ParcelStatus.CREATED, OwnerType.MERCHANT,
                        null, UUID.randomUUID(), "order", "D1", "D", "Z", "D2", "D", "Z", "P", "D", Instant.now()));
        NotificationContactResolver.Contact recipient = resolver.resolve(parcelEvent, notification("RECIPIENT"));
        assertEquals("/internal/v1/parcels/" + parcelId + "/recipient", lastPath.get());
        assertEquals("dropoff@example.test", recipient.email());

        UUID merchantId = UUID.randomUUID();
        EventEnvelope<?> merchantEvent = EventEnvelope.create(EventTypes.MERCHANT_STATUS_CHANGED, Instant.now(),
                UUID.randomUUID(), new MerchantStatusChangedData(merchantId, "Merchant Ltd", null,
                        MerchantStatus.APPROVED, null));
        NotificationContactResolver.Contact merchant = resolver.resolve(merchantEvent, notification("MERCHANT"));
        assertEquals("/internal/v1/merchants/" + merchantId, lastPath.get());
        assertEquals("merchant@example.test", merchant.email());
    }

    @Test
    void serviceErrorsBecomeSanitizedProcessingFailures() {
        failRequests.set(true);
        assertThrows(NotificationProcessingException.class, () -> resolver.resolve(
                EventEnvelope.create(EventTypes.PARCEL_CREATED, Instant.now(), UUID.randomUUID(),
                        new ParcelCreatedData(UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED,
                                OwnerType.CUSTOMER, UUID.randomUUID(), null, null, "D1", "D", "Z", "D2", "D", "Z", "P", "D", Instant.now())),
                notification("SENDER")));
    }

    private static Notification notification(String kind) {
        Notification notification = new Notification();
        notification.setRecipientKind(kind);
        return notification;
    }
}
