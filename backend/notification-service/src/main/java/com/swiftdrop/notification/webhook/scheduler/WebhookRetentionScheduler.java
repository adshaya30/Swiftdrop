package com.swiftdrop.notification.webhook.scheduler;

import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.webhook.repository.WebhookDeliveryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily retention cleanup job for webhook deliveries and notifications ((S) FR-WHK-010).
 * Deletes webhook_deliveries older than 30 days and notifications rows older than 90 days.
 */
@Component
public class WebhookRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetentionScheduler.class);

    private final WebhookDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;

    public WebhookRetentionScheduler(WebhookDeliveryRepository deliveryRepository,
                                   NotificationRepository notificationRepository) {
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository");
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldRecords() {
        log.info("Starting daily retention cleanup for webhook deliveries and notifications (FR-WHK-010)");

        Instant webhookCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deletedWebhooks = deliveryRepository.deleteOlderThan(webhookCutoff);
        log.info("Deleted {} webhook_deliveries rows older than 30 days", deletedWebhooks);

        Instant notificationCutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int deletedNotifications = notificationRepository.deleteOlderThan(notificationCutoff);
        log.info("Deleted {} notifications rows older than 90 days", deletedNotifications);
    }
}

