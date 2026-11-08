package com.swiftdrop.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.swiftdrop.common.security.SecurityRules;
import java.util.List;
import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class DeliveryApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeliveryApplication.class, args);
	}

	@Bean
	SecurityRules securityRules() {
		return new SecurityRules() {
			@Override
			public List<String> publicPaths() {
				return List.of();
			}

			@Override
			public List<String> passwordChangeWhitelist() {
				return List.of();
			}
		};
	}

	@Bean
	Clock deliveryClock() {
		return Clock.systemUTC();
	}

}
