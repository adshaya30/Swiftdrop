package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link User} entities.
 * SRS v2.1 – FR-AUTH-001
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
}
