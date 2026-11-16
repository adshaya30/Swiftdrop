package com.swiftdrop.auth.merchant.dto;

import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of GET /internal/v1/merchants/{merchantId} (SRS 7.4, FR-AUTH-022): used by parcel-service to check that a
 * merchant is APPROVED with an ACTIVE user and to read its discount, and by notification-service for the email.
 */
public record InternalMerchantView(
        UUID id,
        UUID userId,
        String businessName,
        MerchantStatus status,
        UserStatus userStatus,
        BigDecimal discountPercent,
        String contactEmail) {

    @Override
    public String toString() {
        return "InternalMerchantView[id=" + id + ", status=" + status + "]";
    }
}
