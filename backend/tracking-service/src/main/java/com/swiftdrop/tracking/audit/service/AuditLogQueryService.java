package com.swiftdrop.tracking.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.tracking.audit.dto.AuditLogResponse;
import com.swiftdrop.tracking.audit.repository.AuditLogQueryRepository;
import com.swiftdrop.tracking.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-TRK-005: admin audit-log search. Filters are combined with AND, results are newest first and always paged
 * (no unbounded query). {@code from} and {@code to} are inclusive dates in Asia/Colombo (SRS section 4 conventions).
 */
@Service
@Transactional(readOnly = true)
public class AuditLogQueryService {

    static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private final AuditLogQueryRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(AuditLogQueryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Pagination<AuditLogResponse> search(String action, UUID actorId, LocalDate from, LocalDate to,
                                               int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new FieldError("from", "from must not be after to")));
        }
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"),
                        from.atStartOfDay(COLOMBO).toInstant()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.<Instant>get("occurredAt"),
                        to.plusDays(1).atStartOfDay(COLOMBO).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<AuditLog> result = repository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        return Pagination.of(result.getContent().stream().map(this::toResponse).toList(), page, size,
                result.getTotalElements());
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getService(), a.getActorUserId(), a.getActorRole(), a.getAction(),
                a.getEntityType(), a.getEntityId(), parse(a.getDetails()), a.getOccurredAt());
    }

    private JsonNode parse(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(details);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
