package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.entity.CourierProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for CourierProfile entities.
 * SRS v2.1 – FR-AUTH-020
 */
public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {
    Optional<CourierProfile> findByUserId(UUID userId);
}

