package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for transactional OutboxEvent records.
 * SRS v2.1 – Section 3.5, FR-AUTH-023
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByEventType(String eventType);
}

