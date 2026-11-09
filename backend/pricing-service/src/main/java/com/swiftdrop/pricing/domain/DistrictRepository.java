package com.swiftdrop.pricing.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<District, String> {
    List<District> findByZoneActiveTrueOrderByNameAsc();
    boolean existsByZoneId(UUID zoneId);
}
