package com.swiftdrop.tracking.repository;

import com.swiftdrop.tracking.entity.TrackingParcel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingParcelRepository extends JpaRepository<TrackingParcel, UUID> {

    Optional<TrackingParcel> findByTrackingNumber(String trackingNumber);

    Optional<TrackingParcel> findByParcelId(UUID parcelId);
}

