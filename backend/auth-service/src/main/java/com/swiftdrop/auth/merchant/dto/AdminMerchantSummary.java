package com.swiftdrop.auth.merchant.dto;

import com.swiftdrop.auth.entity.MerchantStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of GET /api/v1/admin/merchants (FR-AUTH-017). */
public record AdminMerchantSummary(
        UUID id,
        String businessName,
        String city,
        MerchantStatus status,
        BigDecimal discountPercent,
        Instant createdAt) {
}
