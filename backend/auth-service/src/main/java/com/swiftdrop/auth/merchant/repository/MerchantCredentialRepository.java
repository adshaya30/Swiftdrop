package com.swiftdrop.auth.merchant.repository;

import com.swiftdrop.auth.merchant.entity.MerchantCredential;
import com.swiftdrop.common.enums.CredentialStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantCredentialRepository extends JpaRepository<MerchantCredential, UUID> {

    Optional<MerchantCredential> findByClientId(String clientId);

    /** Own credential only: another merchant's id gives an empty result (answered as 404). */
    Optional<MerchantCredential> findByIdAndMerchantId(UUID id, UUID merchantId);

    Page<MerchantCredential> findByMerchantId(UUID merchantId, Pageable pageable);

    long countByMerchantIdAndStatus(UUID merchantId, CredentialStatus status);
}
