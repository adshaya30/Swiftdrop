package com.swiftdrop.notification.controller;

import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FR-NTF-008: GET /internal/v1/notifications?limit=50 (header X-Internal-Key)
 * Lists recent notifications incl. SMS bodies for demos.
 */
@RestController
@RequestMapping("/internal/v1/notifications")
public class InternalNotificationController {

    private final NotificationRepository notificationRepository;

    public InternalNotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<Notification> listRecent(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 50));
        return notificationRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .limit(cappedLimit)
                .toList();
    }
}

