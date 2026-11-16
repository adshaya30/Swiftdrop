package com.swiftdrop.parcel.order.client;

import com.swiftdrop.common.client.InternalRestClientFactory;
import com.swiftdrop.common.enums.MerchantStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * parcel-service to auth-service (SRS 3.3): merchant status, user status and discount for merchant order create and
 * cancel. Contract: {@code GET /internal/v1/merchants/{merchantId}} (SRS 7.4, FR-AUTH-022).
 */
@Component
public class AuthClient {

    private final RestClient client;

    public AuthClient(InternalRestClientFactory factory, @Value("${parcel.auth-service-url}") String baseUrl) {
        this.client = factory.create(baseUrl);
    }

    /**
     * @return the merchant; a callee business error (for example 404 NOT_FOUND) is rethrown unchanged by the shared
     *         factory; connection failures, timeouts and 5xx become 503 SERVICE_UNAVAILABLE
     */
    public Merchant merchant(UUID merchantId) {
        try {
            Merchant merchant = client.get().uri("/internal/v1/merchants/{merchantId}", merchantId)
                    .retrieve().body(Merchant.class);
            if (merchant == null) {
                throw unavailable();
            }
            return merchant;
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private static DomainException unavailable() {
        return new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "A dependent service is unavailable");
    }

    /** SRS 7.4 body. contactEmail is personal data: never log it. */
    public record Merchant(UUID id, UUID userId, String businessName, MerchantStatus status, UserStatus userStatus,
                           BigDecimal discountPercent, String contactEmail) {

        @Override
        public String toString() {
            return "Merchant[id=" + id + ", status=" + status + ", userStatus=" + userStatus + "]";
        }
    }
}
