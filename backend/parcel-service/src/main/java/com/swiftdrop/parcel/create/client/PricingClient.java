package com.swiftdrop.parcel.create.client;

import com.swiftdrop.common.client.InternalRestClientFactory;
import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.parcel.create.dto.ParcelInput;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** FR-PAR-005, SRS 7.4: pricing owns all fees; shared factory owns keys, trace headers and 5s/10s timeouts. */
@Component
public class PricingClient {
    private final RestClient client;

    public PricingClient(InternalRestClientFactory factory,
                         @Value("${parcel.pricing-service-url}") String baseUrl) {
        this.client = factory.create(baseUrl);
    }

    public Calculation calculate(ParcelInput input, BigDecimal discountPercent) {
        var request = new CalculationRequest(input.pickup().districtCode(), input.dropoff().districtCode(),
                input.parcel().weightKg(), input.parcel().size(), discountPercent);
        try {
            Calculation result = client.post().uri("/internal/v1/pricing/calculate")
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(Calculation.class);
            if (result == null) {
                throw unavailable();
            }
            return result;
        } catch (RestClientException e) {
            // DomainException from the shared factory is not caught: preserve business errors unchanged.
            throw unavailable();
        }
    }

    private static DomainException unavailable() {
        return new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "A dependent service is unavailable");
    }

    public record CalculationRequest(String originDistrictCode, String destinationDistrictCode,
                                     BigDecimal weightKg, ParcelSize size, BigDecimal discountPercent) { }
    public record Zone(String code, String name) { }
    public record District(String code, String name) { }
    public record Calculation(String currency, Zone originZone, Zone destinationZone,
                              BigDecimal baseFee, BigDecimal weightFee, BigDecimal sizeSurcharge,
                              BigDecimal discountAmount, BigDecimal total,
                              District originDistrict, District destinationDistrict,
                              BigDecimal subtotal, BigDecimal discountPercent) { }
}
