package com.swiftdrop.tracking.repository;

import com.swiftdrop.tracking.entity.TrackingEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Append-only repository for tracking events (FR-TRK-006).
 * Exposes NO update or delete methods. The database trigger (FR-TRK-007)
 * enforces this at the database level.
 */
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    @Query("SELECT e FROM TrackingEvent e WHERE e.parcelId = :parcelId ORDER BY e.sequence ASC")
    List<TrackingEvent> findByParcelIdOrderBySequenceAsc(@Param("parcelId") UUID parcelId);

    /** Used to detect stale sequences. */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM TrackingEvent e " +
           "WHERE e.parcelId = :parcelId AND e.sequence = :sequence")
    boolean existsByParcelIdAndSequence(@Param("parcelId") UUID parcelId, @Param("sequence") int sequence);
}

