package com.swiftdrop.tracking;

import com.swiftdrop.common.events.EventConsumerSupport;
import com.swiftdrop.common.events.ProcessedEvents;
import com.swiftdrop.tracking.repository.AuditLogRepository;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import com.swiftdrop.tracking.repository.TrackingParcelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "INTERNAL_API_KEY=test-internal-api-key-12345678901234567890")
class TrackingApplicationTests {

	@MockitoBean
	private AuditLogRepository auditLogRepository;

	@MockitoBean
	private TrackingEventRepository trackingEventRepository;

	@MockitoBean
	private TrackingParcelRepository trackingParcelRepository;

	@MockitoBean
	private ProcessedEvents processedEvents;

	@MockitoBean
	private EventConsumerSupport eventConsumerSupport;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void contextLoads() {
	}

}
