package com.swiftdrop.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Merchant records in auth-service.
 * SRS v2.1 – FR-AUTH-003, FR-AUTH-004
 */
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByUserId(UUID userId);
}
