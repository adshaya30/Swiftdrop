package com.swiftdrop.notification;

import com.swiftdrop.common.events.EventConsumerSupport;
import com.swiftdrop.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "INTERNAL_API_KEY=test-internal-api-key-12345678901234567890")
class NotificationApplicationTests {

	@MockitoBean
	private NotificationRepository notificationRepository;

	@MockitoBean
	private EventConsumerSupport eventConsumerSupport;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void contextLoads() {
	}

}
