package com.swiftdrop.delivery.domain;

import jakarta.persistence.LockModeType;
import com.swiftdrop.common.enums.ParcelStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {

    long countByCourierIdAndStatusIn(UUID courierId, java.util.Collection<com.swiftdrop.common.enums.ParcelStatus> statuses);

    long countByCourierIdIsNullAndStatus(com.swiftdrop.common.enums.ParcelStatus status);

    long countByStatusIn(java.util.Collection<com.swiftdrop.common.enums.ParcelStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from Delivery delivery where delivery.parcelId = :parcelId")
    Optional<Delivery> findByIdForUpdate(@Param("parcelId") UUID parcelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from Delivery delivery where delivery.parcelId = :parcelId and delivery.courierId = :courierId")
    Optional<Delivery> findByParcelIdAndCourierIdForUpdate(@Param("parcelId") UUID parcelId,
                                                           @Param("courierId") UUID courierId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select delivery from Delivery delivery where delivery.courierId = :courierId "
            + "and delivery.status = :status order by delivery.parcelCreatedAt asc")
        List<Delivery> findByCourierIdAndStatusForUpdate(@Param("courierId") UUID courierId,
                                  @Param("status") ParcelStatus status);
}