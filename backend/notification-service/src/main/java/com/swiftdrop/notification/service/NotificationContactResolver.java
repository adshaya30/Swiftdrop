package com.swiftdrop.notification.service;

import com.swiftdrop.common.client.InternalRestClientFactory;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.notification.entity.Notification;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Fetches destinations at send time through the SRS internal contracts. */
@Component
public class NotificationContactResolver {
    private final RestClient auth;
    private final RestClient parcel;

    public NotificationContactResolver(InternalRestClientFactory factory,
            @Value("${notification.auth-service-url}") String authUrl,
            @Value("${notification.parcel-service-url}") String parcelUrl) {
        this.auth = factory.create(authUrl);
        this.parcel = factory.create(parcelUrl);
    }

    public Contact resolve(EventEnvelope<?> event, Notification notification) {
        try {
            String kind = notification.getRecipientKind();
            if ("SENDER".equals(kind)) {
                UUID customerId = customerId(event);
                if (customerId == null) return Contact.empty();
                UserContact user = auth.get().uri("/internal/v1/users/{customerId}/contact", customerId)
                        .retrieve().body(UserContact.class);
                return user == null ? Contact.empty() : new Contact(user.email(), user.phone(), user.fullName(), null);
            }
            if ("RECIPIENT".equals(kind)) {
                UUID parcelId = parcelId(event);
                if (parcelId == null) throw new NotificationProcessingException();
                Recipient recipient = parcel.get().uri("/internal/v1/parcels/{parcelId}/recipient", parcelId)
                        .retrieve().body(Recipient.class);
                return recipient == null ? Contact.empty()
                        : new Contact(recipient.email(), recipient.phone(), null, null);
            }
            if ("MERCHANT".equals(kind)) {
                UUID merchantId = merchantId(event);
                if (merchantId == null) return Contact.empty();
                Merchant merchant = auth.get().uri("/internal/v1/merchants/{merchantId}", merchantId)
                        .retrieve().body(Merchant.class);
                return merchant == null ? Contact.empty()
                        : new Contact(merchant.contactEmail(), null, null, null);
            }
            throw new NotificationProcessingException();
        } catch (NotificationProcessingException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Never retain or log a remote error body: it can echo personal data or credentials.
            throw new NotificationProcessingException();
        }
    }

    private static UUID customerId(EventEnvelope<?> event) {
        if (EventTypes.PARCEL_CREATED.equals(event.eventType())) {
            return ((ParcelCreatedData) event.data()).customerId();
        }
        if (EventTypes.PARCEL_STATUS_CHANGED.equals(event.eventType())) {
            return ((ParcelStatusChangedData) event.data()).customerId();
        }
        return null;
    }

    private static UUID parcelId(EventEnvelope<?> event) {
        if (EventTypes.PARCEL_CREATED.equals(event.eventType())) return ((ParcelCreatedData) event.data()).parcelId();
        if (EventTypes.PARCEL_STATUS_CHANGED.equals(event.eventType())) return ((ParcelStatusChangedData) event.data()).parcelId();
        return null;
    }

    private static UUID merchantId(EventEnvelope<?> event) {
        if (EventTypes.MERCHANT_STATUS_CHANGED.equals(event.eventType()))
            return ((MerchantStatusChangedData) event.data()).merchantId();
        if (EventTypes.PARCEL_CREATED.equals(event.eventType())) return ((ParcelCreatedData) event.data()).merchantId();
        if (EventTypes.PARCEL_STATUS_CHANGED.equals(event.eventType())) return ((ParcelStatusChangedData) event.data()).merchantId();
        return null;
    }

    public record Contact(String email, String phone, String fullName, String businessName) {
        public static Contact empty() { return new Contact(null, null, null, null); }
        @Override public String toString() { return "Contact[masked]"; }
    }

    private record UserContact(UUID id, String fullName, String email, String phone) {
        @Override public String toString() { return "UserContact[id=" + id + "]"; }
    }
    private record Recipient(String name, String phone, String email) {
        @Override public String toString() { return "Recipient[masked]"; }
    }
    private record Merchant(UUID id, UUID userId, String businessName, String status, String userStatus,
                            Object discountPercent, String contactEmail) {
        @Override public String toString() { return "Merchant[id=" + id + ", status=" + status + "]"; }
    }
}
