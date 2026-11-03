package com.swiftdrop.auth.dto.response;

import com.swiftdrop.auth.entity.MerchantStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Summary of merchant information embedded in UserProfileResponse when role is MERCHANT.
 * FR-AUTH-012
 */
public record MerchantSummaryResponse(
        UUID id,
        String businessName,
        MerchantStatus status,
        BigDecimal discountPercent
) {}
