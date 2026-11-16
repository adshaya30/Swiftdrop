package com.swiftdrop.parcel.lifecycle.repository;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.domain.Parcel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * FR-PAR-013 read-only aggregate queries. Ranges are half-open instants [from, to) computed from Asia/Colombo
 * dates by the service; the daily buckets are cut in Asia/Colombo by the database (BR-18).
 */
public interface AdminReportRepository extends Repository<Parcel, UUID> {

    @Query("select count(p) from Parcel p where p.createdAt >= :from and p.createdAt < :to")
    long countCreated(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(p) from Parcel p where p.createdAt >= :from and p.createdAt < :to and p.terminalAt is null")
    long countInProgress(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select p.status as status, count(p) as total from Parcel p "
            + "where p.terminalAt >= :from and p.terminalAt < :to group by p.status")
    List<StatusCount> countTerminalByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select to_char(created_at at time zone 'Asia/Colombo', 'YYYY-MM-DD') as bucket, count(*) as total "
            + "from parcels where created_at >= :from and created_at < :to group by 1", nativeQuery = true)
    List<DayCount> createdPerDay(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select to_char(terminal_at at time zone 'Asia/Colombo', 'YYYY-MM-DD') as bucket, "
            + "status as status, count(*) as total from parcels "
            + "where terminal_at >= :from and terminal_at < :to group by 1, 2", nativeQuery = true)
    List<DayStatusCount> terminalPerDay(@Param("from") Instant from, @Param("to") Instant to);

    interface StatusCount {
        ParcelStatus getStatus();

        long getTotal();
    }

    interface DayCount {
        String getBucket();

        long getTotal();
    }

    interface DayStatusCount {
        String getBucket();

        String getStatus();

        long getTotal();
    }
}
