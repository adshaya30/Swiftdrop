package com.swiftdrop.notification.webhook.repository;

import com.swiftdrop.notification.webhook.entity.MerchantWebhookConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link MerchantWebhookConfig}.
 */
@Repository
public interface MerchantWebhookConfigRepository extends JpaRepository<MerchantWebhookConfig, UUID> {
    Optional<MerchantWebhookConfig> findByMerchantId(UUID merchantId);
}

