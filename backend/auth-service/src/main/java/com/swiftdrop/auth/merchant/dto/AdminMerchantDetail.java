package com.swiftdrop.auth.merchant.dto;

import com.swiftdrop.auth.entity.MerchantStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Admin view of one merchant: merchant fields plus the contact person (FR-AUTH-017). Never serialised in logs. */
public record AdminMerchantDetail(
        UUID id,
        UUID userId,
        String businessName,
        String businessRegistrationNo,
        String contactName,
        String email,
        String phone,
        String addressLine1,
        String city,
        MerchantStatus status,
        String statusReason,
        BigDecimal discountPercent,
        Instant decidedAt,
        UUID decidedBy,
        Instant createdAt,
        Instant updatedAt) {
}
