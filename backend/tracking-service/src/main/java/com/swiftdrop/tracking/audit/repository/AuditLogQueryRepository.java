package com.swiftdrop.tracking.audit.repository;

import com.swiftdrop.tracking.entity.AuditLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** FR-TRK-005: filtered, paged reads and the retention purge of {@code audit_logs}. */
public interface AuditLogQueryRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Returns the number of deleted rows. */
    @Modifying
    @Query("delete from AuditLog a where a.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
