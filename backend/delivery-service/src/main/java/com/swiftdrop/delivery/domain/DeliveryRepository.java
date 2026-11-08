package com.swiftdrop.delivery.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from Delivery delivery where delivery.parcelId = :parcelId")
    Optional<Delivery> findByIdForUpdate(@Param("parcelId") UUID parcelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from Delivery delivery where delivery.parcelId = :parcelId and delivery.courierId = :courierId")
    Optional<Delivery> findByParcelIdAndCourierIdForUpdate(@Param("parcelId") UUID parcelId,
                                                           @Param("courierId") UUID courierId);
}