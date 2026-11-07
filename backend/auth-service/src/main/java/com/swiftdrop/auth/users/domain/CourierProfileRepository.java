package com.swiftdrop.auth.users.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {
    Optional<CourierProfile> findByUserId(UUID userId);
}

