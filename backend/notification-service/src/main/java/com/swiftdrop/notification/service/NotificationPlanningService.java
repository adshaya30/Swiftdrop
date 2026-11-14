package com.swiftdrop.notification.service;

import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.service.NotificationPlanner.PlannedNotification;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists only non-contact notification metadata in a short transaction. */
@Service
public class NotificationPlanningService {
    private final NotificationRepository repository;

    public NotificationPlanningService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<Notification> plan(EventEnvelope<?> event, String trackingNumber,
                                   List<PlannedNotification> planned) {
        for (PlannedNotification item : planned) {
            repository.insertIfAbsent(event.eventId(), event.eventType(), trackingNumber,
                    item.channel().name(), item.recipientKind().name(), item.templateCode());
        }
        return repository.findAllByEventId(event.eventId());
    }
}
