package com.swiftdrop.notification.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.service.NotificationPlanner;
import com.swiftdrop.notification.service.NotificationTemplates;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationEventHandlersTest {

    private final NotificationRepository notificationRepo = mock(NotificationRepository.class);
    private final NotificationEventHandlers handlers = new NotificationEventHandlers(
            notificationRepo, new NotificationPlanner(), new NotificationTemplates("https://swiftdrop.example"));

    @Test
    void parcelCreatedPersistsPlannedNotifications() {
        UUID eventId = UUID.randomUUID();
        var data = new ParcelCreatedData(
                UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED, OwnerType.CUSTOMER,
                UUID.randomUUID(), null, null, "D1", "District", "Zone", "D2", "Destination", "Zone",
                "Pickup", "Dropoff", Instant.now());
        var envelope = new EventEnvelope<>(eventId, EventTypes.PARCEL_CREATED, 1, Instant.now(),
                UUID.randomUUID(), data);

        handlers.handle(envelope);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo, times(2)).save(saved.capture());
        List<Notification> notifications = saved.getAllValues();
        assertTrue(notifications.stream().allMatch(n -> eventId.equals(n.getEventId())));
        assertTrue(notifications.stream().allMatch(n -> "SENDER".equals(n.getRecipientKind())));
        assertTrue(notifications.stream().allMatch(n -> "parcel_created_sender".equals(n.getTemplateCode())));
        assertEquals(List.of("EMAIL", "SMS"), notifications.stream().map(Notification::getChannel).toList());
        assertTrue(notifications.stream().allMatch(n -> n.getBodyPreview().contains(data.trackingNumber())));
    }

    @Test
    void existingNotificationIsNotSavedAgain() {
        when(notificationRepo.findByEventIdAndChannelAndRecipientKind(
                org.mockito.ArgumentMatchers.any(), anyString(), anyString()))
                .thenReturn(Optional.of(mock(Notification.class)));
        var data = new ParcelCreatedData(
                UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED, OwnerType.CUSTOMER,
                UUID.randomUUID(), null, null, "D1", "District", "Zone", "D2", "Destination", "Zone",
                "Pickup", "Dropoff", Instant.now());
        var envelope = EventEnvelope.create(EventTypes.PARCEL_CREATED, Instant.now(), UUID.randomUUID(), data);

        handlers.handle(envelope);

        verify(notificationRepo, never()).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }
}
