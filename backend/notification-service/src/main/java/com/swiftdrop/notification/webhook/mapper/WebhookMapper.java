package com.swiftdrop.notification.webhook.mapper;

import com.swiftdrop.notification.webhook.dto.response.WebhookDeliveryItemResponse;
import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import org.springframework.stereotype.Component;

/**
 * Mapper for webhook entities to DTO responses.
 */
@Component
public class WebhookMapper {

    public WebhookDeliveryItemResponse toItemResponse(WebhookDelivery delivery) {
        if (delivery == null) {
            return null;
        }
        return new WebhookDeliveryItemResponse(
                delivery.getId(),
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getTrackingNumber(),
                delivery.getTargetUrl(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getLastHttpStatus(),
                delivery.getLastError(),
                delivery.getCreatedAt(),
                delivery.getLastAttemptAt(),
                delivery.getCompletedAt()
        );
    }
}

