package com.swiftdrop.auth.profile.dto;

import com.swiftdrop.auth.entity.MerchantStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record MerchantSummaryResponse(
        UUID id,
        String businessName,
        MerchantStatus status,
        BigDecimal discountPercent
) {}

