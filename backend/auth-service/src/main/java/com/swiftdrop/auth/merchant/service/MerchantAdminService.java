package com.swiftdrop.auth.merchant.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import com.swiftdrop.auth.merchant.dto.AdminMerchantDetail;
import com.swiftdrop.auth.merchant.dto.AdminMerchantSummary;
import com.swiftdrop.auth.merchant.mapper.MerchantAdminMapper;
import com.swiftdrop.auth.merchant.repository.MerchantAdminRepository;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import com.swiftdrop.common.paging.Pagination;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant administration. SRS v2.1 - FR-AUTH-017, FR-AUTH-023, BR-12 (lifecycle), BR-23 (suspend revokes refresh
 * tokens), BR-28 (no contact data in events or audit details).
 *
 * <p>Every status action runs in one transaction: row lock, status change, {@code merchant.status.changed} outbox
 * event and {@code audit.recorded} outbox event. If any step fails, nothing is stored.</p>
 */
@Service
public class MerchantAdminService {

    private static final Logger log = LoggerFactory.getLogger(MerchantAdminService.class);

    /** Sort fields accepted by the list endpoint (BR-26: whitelisted). */
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "businessName", "status");
    static final int MAX_PAGE_SIZE = 100;
    static final BigDecimal MAX_DISCOUNT = new BigDecimal("50.00");

    private final MerchantAdminRepository merchantRepository;
    private final UserRepository userRepository;
    private final MerchantAdminMapper mapper;
    private final OutboxService outboxService;
    private final AuditEvents auditEvents;
    private final RefreshTokenService refreshTokenService;

    public MerchantAdminService(MerchantAdminRepository merchantRepository, UserRepository userRepository,
                                MerchantAdminMapper mapper, OutboxService outboxService, AuditEvents auditEvents,
                                RefreshTokenService refreshTokenService) {
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.outboxService = outboxService;
        this.auditEvents = auditEvents;
        this.refreshTokenService = refreshTokenService;
    }

    // ------------------------------------------------------------------ queries

    /** GET /admin/merchants: filters status and q (business name prefix), paginated and sorted. */
    @Transactional(readOnly = true)
    public Pagination<AdminMerchantSummary> list(String status, String q, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw validation("size", "page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        MerchantStatus statusFilter = parseStatus(status);
        Specification<Merchant> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("businessName")),
                        escapeLike(q.trim().toLowerCase()) + "%", '\\'));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Merchant> result = merchantRepository.findAll(spec, PageRequest.of(page, size, parseSort(sort)));
        return Pagination.of(result.getContent().stream().map(mapper::toSummary).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminMerchantDetail get(UUID id) {
        return detail(merchantRepository.findById(id).orElseThrow(MerchantAdminService::notFound));
    }

    // ------------------------------------------------------------------ status actions (BR-12)

    /** From PENDING or REJECTED. */
    @Transactional
    public AdminMerchantDetail approve(UUID id, UUID adminId) {
        return changeStatus(id, adminId, Set.of(MerchantStatus.PENDING, MerchantStatus.REJECTED),
                MerchantStatus.APPROVED, null, "MERCHANT_APPROVED");
    }

    /** From PENDING; reason required. */
    @Transactional
    public AdminMerchantDetail reject(UUID id, UUID adminId, String reason) {
        return changeStatus(id, adminId, Set.of(MerchantStatus.PENDING), MerchantStatus.REJECTED,
                checkedReason(reason), "MERCHANT_REJECTED");
    }

    /** From APPROVED; reason required. Revokes the merchant user's refresh tokens (BR-23). */
    @Transactional
    public AdminMerchantDetail suspend(UUID id, UUID adminId, String reason) {
        return changeStatus(id, adminId, Set.of(MerchantStatus.APPROVED), MerchantStatus.SUSPENDED,
                checkedReason(reason), "MERCHANT_SUSPENDED");
    }

    /** From SUSPENDED back to APPROVED. */
    @Transactional
    public AdminMerchantDetail reinstate(UUID id, UUID adminId) {
        return changeStatus(id, adminId, Set.of(MerchantStatus.SUSPENDED), MerchantStatus.APPROVED,
                null, "MERCHANT_REINSTATED");
    }

    /** PATCH .../discount: 0.00 to 50.00. Audited; it is not a status change, so no merchant.status.changed event. */
    @Transactional
    public AdminMerchantDetail setDiscount(UUID id, UUID adminId, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.signum() < 0 || discountPercent.compareTo(MAX_DISCOUNT) > 0
                || discountPercent.stripTrailingZeros().scale() > 2) {
            throw validation("discountPercent", "Discount percent must be between 0.00 and 50.00 with two decimals");
        }
        Merchant merchant = merchantRepository.findByIdForUpdate(id).orElseThrow(MerchantAdminService::notFound);
        BigDecimal previous = merchant.getDiscountPercent();
        merchant.setDiscountPercent(discountPercent.setScale(2));
        merchantRepository.save(merchant);

        auditEvents.record(adminId, ActorRole.ADMIN, "MERCHANT_DISCOUNT_CHANGED", "MERCHANT", id.toString(),
                Map.of("previousDiscountPercent", previous.toPlainString(),
                        "discountPercent", merchant.getDiscountPercent().toPlainString()));
        log.info("Merchant discount changed: merchantId={}", id);
        return detail(merchant);
    }

    // ------------------------------------------------------------------ internals

    private AdminMerchantDetail changeStatus(UUID id, UUID adminId, Set<MerchantStatus> allowedFrom,
                                             MerchantStatus target, String reason, String auditAction) {
        Merchant merchant = merchantRepository.findByIdForUpdate(id).orElseThrow(MerchantAdminService::notFound);
        MerchantStatus previous = merchant.getStatus();
        if (!allowedFrom.contains(previous)) {
            throw new DomainException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A merchant that is " + previous + " cannot become " + target + ".");
        }

        merchant.setStatus(target);
        merchant.setStatusReason(reason);
        merchant.setDecidedAt(Instant.now());
        merchant.setDecidedBy(adminId);
        merchantRepository.save(merchant);

        // SRS 3.5: merchant.status.changed (the reason travels here, never in the audit details)
        outboxService.add(EventTypes.MERCHANT_STATUS_CHANGED, new MerchantStatusChangedData(
                merchant.getId(), merchant.getBusinessName(),
                com.swiftdrop.common.enums.MerchantStatus.valueOf(previous.name()),
                com.swiftdrop.common.enums.MerchantStatus.valueOf(target.name()), reason));
        // FR-AUTH-023: details hold statuses only, no reason text, no contact data
        auditEvents.record(adminId, ActorRole.ADMIN, auditAction, "MERCHANT", id.toString(),
                Map.of("previousStatus", previous.name(), "status", target.name()));

        if (target == MerchantStatus.SUSPENDED) {
            refreshTokenService.revokeAllForUser(merchant.getUserId()); // BR-23
        }
        log.info("Merchant {} -> {}: merchantId={}", previous, target, id);
        return detail(merchant);
    }

    private AdminMerchantDetail detail(Merchant merchant) {
        User user = userRepository.findById(merchant.getUserId())
                .orElseThrow(() -> new IllegalStateException("Merchant without user: " + merchant.getId()));
        return mapper.toDetail(merchant, user);
    }

    /** Reason of 10-300 characters after trimming (BR-12). */
    private static String checkedReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 10 || trimmed.length() > 300) {
            throw validation("reason", "Reason must be between 10 and 300 characters");
        }
        return trimmed;
    }

    private static MerchantStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return MerchantStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw validation("status", "Status must be one of PENDING, APPROVED, REJECTED, SUSPENDED");
        }
    }

    /** sort=field,dir with whitelisted fields; default createdAt,desc; id as tie breaker. */
    private static Sort parseSort(String sort) {
        String field = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            field = parts[0].trim();
            if (!SORT_FIELDS.contains(field)) {
                throw validation("sort", "Sort field must be one of " + String.join(", ", SORT_FIELDS));
            }
            if (parts.length > 1) {
                direction = switch (parts[1].trim().toLowerCase()) {
                    case "asc" -> Sort.Direction.ASC;
                    case "desc" -> Sort.Direction.DESC;
                    default -> throw validation("sort", "Sort direction must be asc or desc");
                };
            }
        }
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "Merchant not found.");
    }

    private static DomainException validation(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed.",
                List.of(new FieldError(field, message)));
    }
}
