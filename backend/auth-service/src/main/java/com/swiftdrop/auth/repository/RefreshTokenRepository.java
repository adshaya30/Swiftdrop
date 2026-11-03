package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RefreshToken records.
 * SRS v2.1 – FR-AUTH-007, FR-AUTH-008, FR-AUTH-026
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Row-level pessimistic write lock (SELECT ... FOR UPDATE) to prevent race conditions during rotation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithLock(@Param("tokenHash") String tokenHash);

    /**
     * Revokes all refresh tokens in a given family.
     * SRS v2.1 – FR-AUTH-007, FR-AUTH-008
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

    /**
     * Checks if any token in the family has been revoked.
     */
    @Query("SELECT COUNT(r) > 0 FROM RefreshToken r WHERE r.familyId = :familyId AND r.revokedAt IS NOT NULL")
    boolean isFamilyRevoked(@Param("familyId") UUID familyId);

    /**
     * Revokes all active refresh tokens for a user across all families.
     * SRS v2.1 – FR-AUTH-013 (Password Change)
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    /**
     * Purges refresh tokens whose retention deadline has passed (expires_at <= cutoff).
     * Bounded by limit to prevent long-running table locks during periodic cleanup.
     * SRS v2.1 – FR-AUTH-026
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM refresh_tokens WHERE id IN (SELECT id FROM refresh_tokens WHERE expires_at <= :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteExpiredTokensBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    /**
     * Counts the number of refresh tokens whose retention deadline has passed.
     */
    @Query("SELECT COUNT(r) FROM RefreshToken r WHERE r.expiresAt <= :cutoff")
    long countExpiredTokensBefore(@Param("cutoff") Instant cutoff);
}
