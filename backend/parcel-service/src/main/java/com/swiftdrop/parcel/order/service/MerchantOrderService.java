package com.swiftdrop.parcel.order.service;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.parcel.create.dto.CreationRequest;
import com.swiftdrop.parcel.create.dto.CreationResult;
import com.swiftdrop.parcel.create.service.IdempotencyService;
import com.swiftdrop.parcel.create.service.ParcelCreationService;
import com.swiftdrop.parcel.order.dto.MerchantOrderCreatedResponse;
import com.swiftdrop.parcel.order.dto.MerchantOrderCreationRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * FR-PAR-007 through the one shared creation path (FR-PAR-006, master 8.1). Its order is Flow 4.3: idempotency
 * lookup first, then the merchant re-check (BR-23) through the discount resolver, pricing with the merchant discount,
 * the optional expectedFee comparison, and one transaction that also enforces merchantReference uniqueness (BR-22).
 */
@Service
public class MerchantOrderService {

    private final ParcelCreationService creation;
    private final IdempotencyService idempotency;
    private final MerchantEligibility eligibility;
    private final String publicBaseUrl;

    public MerchantOrderService(ParcelCreationService creation, IdempotencyService idempotency,
                                MerchantEligibility eligibility,
                                @Value("${parcel.public-base-url}") String publicBaseUrl) {
        this.creation = creation;
        this.idempotency = idempotency;
        this.eligibility = eligibility;
        this.publicBaseUrl = publicBaseUrl;
    }

    public CreationResult create(UUID merchantId, MerchantOrderCreationRequest body, String idempotencyKey) {
        return creation.create(new CreationRequest(OwnerType.MERCHANT, merchantId, body.toParcelInput(),
                body.expectedFee(), body.merchantReference(), idempotencyKey, idempotency.hashRequest(body),
                () -> eligibility.requireActive(merchantId),
                created -> MerchantOrderCreatedResponse.from(created, publicBaseUrl)));
    }
}
