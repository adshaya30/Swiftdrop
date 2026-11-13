package com.swiftdrop.notification;

import com.swiftdrop.common.events.EventConsumerSupport;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.service.NotificationLockService;
import com.swiftdrop.notification.webhook.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.notification.webhook.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
		"INTERNAL_API_KEY=test-internal-api-key-12345678901234567890",
		"WEBHOOK_SECRET_ENCRYPTION_KEY=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class NotificationApplicationTests {

	@MockitoBean
	private NotificationRepository notificationRepository;

	@MockitoBean
	private MerchantWebhookConfigRepository merchantWebhookConfigRepository;

	@MockitoBean
	private WebhookDeliveryRepository webhookDeliveryRepository;

	@MockitoBean
	private EventConsumerSupport eventConsumerSupport;

	@MockitoBean
	private NotificationLockService notificationLockService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void contextLoads() {
	}

}
