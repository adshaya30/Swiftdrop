package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.entity.DeliveryProof;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {
}

