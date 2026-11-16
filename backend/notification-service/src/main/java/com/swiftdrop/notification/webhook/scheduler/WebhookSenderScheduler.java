package com.swiftdrop.notification.webhook.scheduler;

import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import com.swiftdrop.notification.webhook.service.WebhookClaimService;
import com.swiftdrop.notification.webhook.service.WebhookSenderService;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler polling due webhook deliveries every 2 seconds (FR-WHK-004).
 * Claims rows in a short transaction with FOR UPDATE SKIP LOCKED, then dispatches them outside.
 */
@Component
public class WebhookSenderScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookSenderScheduler.class);
    private static final int BATCH_SIZE = 25;

    private final WebhookClaimService claimService;
    private final WebhookSenderService senderService;

    public WebhookSenderScheduler(WebhookClaimService claimService, WebhookSenderService senderService) {
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.senderService = Objects.requireNonNull(senderService, "senderService");
    }

    @Scheduled(fixedDelay = 2000)
    public void processDueDeliveries() {
        try {
            List<WebhookDelivery> claimed = claimService.claimDueDeliveries(BATCH_SIZE);
            if (!claimed.isEmpty()) {
                log.debug("Claimed {} due webhook deliveries for dispatch", claimed.size());
            }
            for (WebhookDelivery delivery : claimed) {
                try {
                    senderService.dispatchDelivery(delivery);
                } catch (Exception e) {
                    log.error("Unexpected error dispatching webhook delivery {}", delivery.getEventId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error during webhook claim cycle", e);
        }
    }
}

