package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link User} entities.
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-005
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Case-insensitive email lookup.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    /**
     * Simple existence check used for duplicate-email guard before insert.
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    /**
     * Atomically increments failed_login_count and locks account for 15 minutes when threshold (5) is reached.
     * SRS v2.1 – FR-AUTH-005
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE users SET failed_login_count = failed_login_count + 1, " +
            "locked_until = CASE WHEN failed_login_count + 1 >= 5 THEN :lockTime ELSE locked_until END, " +
            "updated_at = NOW() WHERE id = :userId", nativeQuery = true)
    void recordFailedLoginAttempt(@Param("userId") UUID userId, @Param("lockTime") Instant lockTime);

    /**
     * Resets failed login counters and updates last_login_at upon successful login.
     * SRS v2.1 – FR-AUTH-005
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE users SET failed_login_count = 0, locked_until = NULL, last_login_at = :loginTime, updated_at = NOW() WHERE id = :userId", nativeQuery = true)
    void resetFailedLoginLock(@Param("userId") UUID userId, @Param("loginTime") Instant loginTime);
}
