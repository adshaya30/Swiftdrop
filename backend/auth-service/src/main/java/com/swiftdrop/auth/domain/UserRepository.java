package com.swiftdrop.auth.domain;

import com.swiftdrop.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link User} domain entities.
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-005, FR-AUTH-015, FR-AUTH-020
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * Finds user by exact email (stored in lower-case per SRS 6.3).
     */
    Optional<User> findByEmail(String email);

    /**
     * Case-insensitive email lookup for backward compatibility and safe registration checking.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    /**
     * Checks if any user exists with the given role.
     */
    boolean existsByRole(UserRole role);

    /**
     * Finds user by their verified Google subject identifier (sub).
     */
    Optional<User> findByGoogleSub(String googleSub);

    /**
     * Checks if email is already registered.
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    default boolean existsByEmail(String email) {
        return existsByEmailIgnoreCase(email);
    }

    /**
     * Atomically increments failed_login_count and locks account when threshold (5) is reached.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE users SET failed_login_count = failed_login_count + 1, " +
            "locked_until = CASE WHEN failed_login_count + 1 >= 5 THEN :lockTime ELSE locked_until END, " +
            "updated_at = NOW() WHERE id = :userId", nativeQuery = true)
    void recordFailedLoginAttempt(@Param("userId") UUID userId, @Param("lockTime") Instant lockTime);

    /**
     * Resets failed login counters and updates last_login_at upon successful login.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE users SET failed_login_count = 0, locked_until = NULL, last_login_at = :loginTime, updated_at = NOW() WHERE id = :userId", nativeQuery = true)
    void resetFailedLoginLock(@Param("userId") UUID userId, @Param("loginTime") Instant loginTime);
}
