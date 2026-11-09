package com.swiftdrop.auth.merchant.repository;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.entity.MerchantStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Admin queries on merchants (FR-AUTH-017, FR-AUTH-024). The shared MerchantRepository stays untouched. */
public interface MerchantAdminRepository extends JpaRepository<Merchant, UUID>, JpaSpecificationExecutor<Merchant> {

    /** Row lock so two admins cannot move the same merchant at once (BR-12). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Merchant m where m.id = :id")
    Optional<Merchant> findByIdForUpdate(@Param("id") UUID id);

    long countByStatus(MerchantStatus status);
}
