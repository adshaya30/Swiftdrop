package com.swiftdrop.tracking.repository;

import com.swiftdrop.tracking.entity.TrackingEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Append-only repository for tracking events (FR-TRK-006).
 * Exposes save and queries only. Update and delete are not part of this
 * repository. The database trigger (FR-TRK-007) rejects those statements.
 */
public interface TrackingEventRepository extends Repository<TrackingEvent, Long> {

    TrackingEvent save(TrackingEvent event);

    @Query("SELECT e FROM TrackingEvent e WHERE e.parcelId = :parcelId ORDER BY e.sequence ASC")
    List<TrackingEvent> findByParcelIdOrderBySequenceAsc(@Param("parcelId") UUID parcelId);

    /** Used to detect stale sequences. */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM TrackingEvent e " +
           "WHERE e.parcelId = :parcelId AND e.sequence = :sequence")
    boolean existsByParcelIdAndSequence(@Param("parcelId") UUID parcelId, @Param("sequence") int sequence);
}

