package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.entity.OAuthLoginState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for transient OAuth 2.0 PKCE login states.
 * SRS v2.1 – Flow 4.5, FR-AUTH-026
 */
public interface OAuthLoginStateRepository extends JpaRepository<OAuthLoginState, String> {

    Optional<OAuthLoginState> findByStateHash(String stateHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM OAuthLoginState s WHERE s.stateHash = :stateHash")
    Optional<OAuthLoginState> findByStateHashForUpdate(@Param("stateHash") String stateHash);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM OAuthLoginState s WHERE s.stateHash = :stateHash")
    int deleteByStateHash(@Param("stateHash") String stateHash);

    /**
     * Purges expired OAuth login states (FR-AUTH-026 hourly purge).
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM OAuthLoginState s WHERE s.expiresAt <= :cutoff")
    int deleteExpiredStatesBefore(@Param("cutoff") Instant cutoff);
}
