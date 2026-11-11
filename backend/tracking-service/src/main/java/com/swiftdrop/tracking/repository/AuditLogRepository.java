package com.swiftdrop.tracking.repository;

import com.swiftdrop.tracking.entity.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    boolean existsByEventId(UUID eventId);
}

