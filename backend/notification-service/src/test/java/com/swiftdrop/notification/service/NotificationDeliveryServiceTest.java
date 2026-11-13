package com.swiftdrop.notification.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.service.NotificationStateService.Snapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDeliveryServiceTest {
    private final NotificationContactResolver contacts = mock(NotificationContactResolver.class);
    private final EmailSender email = mock(EmailSender.class);
    private final SmsSender sms = mock(SmsSender.class);
    private final NotificationStateService state = mock(NotificationStateService.class);
    private final NotificationLockService locks = mock(NotificationLockService.class);
    private final NotificationLockService.Lock lock = mock(NotificationLockService.Lock.class);
    private final NotificationDeliveryService delivery = new NotificationDeliveryService(
            contacts, new NotificationTemplates("https://public.example"), email, sms, state, locks);
    private final UUID rowId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final Notification row = mock(Notification.class);
    private final EventEnvelope<?> event = EventEnvelope.create(EventTypes.PARCEL_CREATED, Instant.now(),
            UUID.randomUUID(), new ParcelCreatedData(UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED,
                    OwnerType.CUSTOMER, customerId, null, null, "D1", "District", "Zone", "D2", "Destination",
                    "Zone", "Pickup", "Dropoff", Instant.now()));

    @BeforeEach
    void setup() {
        when(row.getId()).thenReturn(rowId);
        when(row.getRecipientKind()).thenReturn("SENDER");
        when(locks.acquire(rowId)).thenReturn(lock);
        when(state.load(rowId)).thenReturn(new Snapshot(rowId, event.eventId(), "EMAIL", "SENDER",
                "parcel_created_sender", "SD1234567890", "PENDING", 0));
        when(contacts.resolve(event, row)).thenReturn(new NotificationContactResolver.Contact(
                "person@example.test", "+94771234567", "Recipient Name", null));
    }

    @Test
    void deliversEmailAndStoresOnlyMaskedDestination() throws Exception {
        delivery.deliver(event, List.of(row));
        verify(email).send(eq("person@example.test"), eq("SwiftDrop: Parcel Created"),
                org.mockito.ArgumentMatchers.contains("https://public.example/track/SD1234567890"));
        verify(state).sent(rowId, 1, "p***@example.test", null);
    }

    @Test
    void missingEmailIsSkippedWithoutSending() throws Exception {
        when(contacts.resolve(event, row)).thenReturn(NotificationContactResolver.Contact.empty());
        delivery.deliver(event, List.of(row));
        verify(state).skipped(rowId, null);
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void smtpFailurePersistsSanitizedFailureAndPropagatesToConsumer() throws Exception {
        doThrow(new Exception("SMTP failed for person@example.test"))
                .when(email).send(anyString(), anyString(), anyString());
        NotificationProcessingException failure = assertThrows(NotificationProcessingException.class,
                () -> delivery.deliver(event, List.of(row)));
        org.junit.jupiter.api.Assertions.assertEquals("Notification processing failed", failure.getMessage());
        verify(state).failed(rowId, 1, "p***@example.test");
    }

    @Test
    void completedRowsAreNeverSentAgain() throws Exception {
        when(state.load(rowId)).thenReturn(new Snapshot(rowId, event.eventId(), "EMAIL", "SENDER",
                "parcel_created_sender", "SD1234567890", "SENT", 1));
        delivery.deliver(event, List.of(row));
        verify(email, never()).send(anyString(), anyString(), anyString());
        verify(state, never()).failed(eq(rowId), anyInt(), any());
    }
}
