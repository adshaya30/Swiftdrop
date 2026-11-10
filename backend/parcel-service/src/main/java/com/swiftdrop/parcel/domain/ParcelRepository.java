package com.swiftdrop.parcel.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Shared by create, lifecycle and order slices; business policies stay in their owning slices. */
public interface ParcelRepository extends JpaRepository<Parcel, UUID>, JpaSpecificationExecutor<Parcel> {
    Optional<Parcel> findByTrackingNumber(String trackingNumber);
    boolean existsByTrackingNumber(String trackingNumber);
}
