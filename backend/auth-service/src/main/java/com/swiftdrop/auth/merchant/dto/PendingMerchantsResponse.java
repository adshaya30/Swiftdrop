package com.swiftdrop.auth.merchant.dto;

/** Body of GET /api/v1/admin/dashboard/merchants (FR-AUTH-024). */
public record PendingMerchantsResponse(long pendingMerchants) {
}
