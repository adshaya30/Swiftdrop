package com.swiftdrop.delivery.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    List<DeliveryAssignment> findByParcelIdOrderByAssignedAtAsc(UUID parcelId);

    Optional<DeliveryAssignment> findByParcelIdAndUnassignedAtIsNull(UUID parcelId);
}