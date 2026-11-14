package com.swiftdrop.parcel.order.repository;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.domain.Parcel;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** FR-PAR-014 read queries for the merchant dashboard; always restricted to one merchant. Read-only. */
public interface MerchantDashboardRepository extends Repository<Parcel, UUID> {

    @Query("select p.status as status, count(p) as total from Parcel p "
            + "where p.merchantId = :merchantId and p.status in :statuses group by p.status")
    List<StatusCount> countByStatus(@Param("merchantId") UUID merchantId,
                                    @Param("statuses") Collection<ParcelStatus> statuses);

    @Query("select p.status as status, count(p) as total from Parcel p "
            + "where p.merchantId = :merchantId and p.createdAt >= :since group by p.status")
    List<StatusCount> countCreatedSince(@Param("merchantId") UUID merchantId, @Param("since") Instant since);

    List<Parcel> findTop10ByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    interface StatusCount {
        ParcelStatus getStatus();

        long getTotal();
    }
}
