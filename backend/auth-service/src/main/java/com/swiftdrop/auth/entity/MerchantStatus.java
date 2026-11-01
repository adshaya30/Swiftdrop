package com.swiftdrop.auth.entity;

/**
 * Status of a merchant account.
 * SRS v2.1 – Section 6.2, FR-AUTH-004, FR-MER-002
 */
public enum MerchantStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}

