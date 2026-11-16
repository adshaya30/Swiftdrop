package com.swiftdrop.notification.webhook.service;

import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import com.swiftdrop.notification.webhook.repository.WebhookDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional claim service for webhook deliveries with lease management (FR-WHK-004).
 */
@Service
public class WebhookClaimService {

    private static final int LEASE_SECONDS = 30;
    private final WebhookDeliveryRepository deliveryRepository;

    public WebhookClaimService(WebhookDeliveryRepository deliveryRepository) {
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository");
    }

    /**
     * Claims up to {@code limit} due deliveries using {@code FOR UPDATE SKIP LOCKED}
     * and moves their {@code nextAttemptAt} forward by a short lease.
     *
     * @param limit maximum deliveries to claim
     * @return list of claimed deliveries
     */
    @Transactional
    public List<WebhookDelivery> claimDueDeliveries(int limit) {
        Instant now = Instant.now();
        List<WebhookDelivery> claimed = deliveryRepository.claimDueDeliveries(now, limit);
        Instant leaseUntil = now.plusSeconds(LEASE_SECONDS);
        for (WebhookDelivery delivery : claimed) {
            delivery.setNextAttemptAt(leaseUntil);
            deliveryRepository.save(delivery);
        }
        return claimed;
    }
}

