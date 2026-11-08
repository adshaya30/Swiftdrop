package com.swiftdrop.pricing.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    Optional<Zone> findByCode(String code);
    boolean existsByName(String name);
}
