package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.entity.ProofPhoto;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProofPhotoRepository extends JpaRepository<ProofPhoto, UUID> {
}

