package com.swiftdrop.delivery.assignment.service;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.common.events.data.DeliveryAssignedData;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.delivery.assignment.dto.AdminDeliveryListItem;
import com.swiftdrop.delivery.assignment.dto.CourierWorkloadResponse;
import com.swiftdrop.delivery.assignment.dto.DeliveryDetailResponse;
import com.swiftdrop.delivery.assignment.dto.DispatchDashboardResponse;
import com.swiftdrop.delivery.assignment.repository.CourierWorkloadQueries;
import com.swiftdrop.delivery.domain.Courier;
import com.swiftdrop.delivery.domain.CourierDeliveryQueries;
import com.swiftdrop.delivery.domain.CourierRepository;
import com.swiftdrop.delivery.domain.Delivery;
import com.swiftdrop.delivery.domain.DeliveryAssignment;
import com.swiftdrop.delivery.domain.DeliveryAssignmentRepository;
import com.swiftdrop.delivery.domain.DeliveryListItem;
import com.swiftdrop.delivery.domain.DeliveryRepository;
import com.swiftdrop.delivery.domain.Scope;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryAdminService {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final List<ParcelStatus> IN_PROGRESS_STATUSES = List.of(
            ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "trackingNumber", "status", "assignedAt");

    private final DeliveryRepository deliveryRepository;
    private final CourierRepository courierRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final CourierDeliveryQueries courierDeliveryQueries;
    private final CourierWorkloadQueries workloadQueries;
    private final OutboxService outboxService;
    private final AuditEvents auditEvents;
    private final Clock clock;

    public DeliveryAdminService(DeliveryRepository deliveryRepository, CourierRepository courierRepository,
                                DeliveryAssignmentRepository assignmentRepository,
                                CourierDeliveryQueries courierDeliveryQueries,
                                CourierWorkloadQueries workloadQueries, OutboxService outboxService,
                                AuditEvents auditEvents, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.courierRepository = courierRepository;
        this.assignmentRepository = assignmentRepository;
        this.courierDeliveryQueries = courierDeliveryQueries;
        this.workloadQueries = workloadQueries;
        this.outboxService = outboxService;
        this.auditEvents = auditEvents;
        this.clock = clock;
    }

    @Transactional
    public DeliveryDetailResponse assign(UUID parcelId, UUID courierId) {
        // FR-DEL-002 and Flow 5: assignment shares the courier update's pessimistic parcel lock.
        Delivery delivery = deliveryRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> notFound("Delivery not found"));
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> notFound("Courier not found"));
        if (delivery.getStatus() != ParcelStatus.CREATED || courier.getStatus() != UserStatus.ACTIVE) {
            throw new DomainException(ErrorCode.PARCEL_NOT_ASSIGNABLE, "This parcel cannot be assigned");
        }
        if (courierId.equals(delivery.getCourierId())) {
            return deliveryDetails(delivery);
        }

        UUID previousCourierId = delivery.getCourierId();
        Instant assignedAt = clock.instant();
        assignmentRepository.findByParcelIdAndUnassignedAtIsNull(parcelId)
                .ifPresent(previous -> previous.close(assignedAt));
        delivery.assignTo(courierId, assignedAt);
        assignmentRepository.save(new DeliveryAssignment(UUID.randomUUID(), parcelId, courierId,
                CurrentUsers.get().userId(), assignedAt));

        String courierFirstName = courier.getFullName().trim().split("\\s+", 2)[0];
        outboxService.add(EventTypes.DELIVERY_ASSIGNED,
                new DeliveryAssignedData(parcelId, courierId, courierFirstName, assignedAt));
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("courierId", courierId.toString());
        if (previousCourierId != null) {
            auditDetails.put("previousCourierId", previousCourierId.toString());
        }
        auditEvents.record("DELIVERY_ASSIGNED", "DELIVERY", parcelId.toString(), auditDetails);
        return deliveryDetails(delivery);
    }

    @Transactional(readOnly = true)
    public Pagination<AdminDeliveryListItem> listDeliveries(
            Boolean assigned, ParcelStatus status, UUID courierId, String originDistrictCode,
            String destinationDistrictCode, LocalDate from, LocalDate to, String query,
            int page, int size, String sort) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Invalid creation date range");
        }
        boolean oldestFirst = Boolean.FALSE.equals(assigned) && status == ParcelStatus.CREATED;
        Sort ordering = resolveSort(sort, oldestFirst);
        PageRequest pageable = PageRequest.of(page, size, ordering);
        Specification<Delivery> filters = (root, criteria, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (assigned != null) {
                predicates.add(assigned ? builder.isNotNull(root.get("courierId")) : builder.isNull(root.get("courierId")));
            }
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (courierId != null) predicates.add(builder.equal(root.get("courierId"), courierId));
            if (originDistrictCode != null && !originDistrictCode.isBlank()) {
                predicates.add(builder.equal(root.get("originDistrictCode"), originDistrictCode));
            }
            if (destinationDistrictCode != null && !destinationDistrictCode.isBlank()) {
                predicates.add(builder.equal(root.get("destinationDistrictCode"), destinationDistrictCode));
            }
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("parcelCreatedAt"), dayStart(from)));
            if (to != null) predicates.add(builder.lessThan(root.get("parcelCreatedAt"), dayStart(to.plusDays(1))));
            if (query != null && !query.isBlank()) {
                predicates.add(builder.like(root.get("trackingNumber"), escapeLike(query.trim()) + "%", '\\'));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<Delivery> result = deliveryRepository.findAll(filters, pageable);
        List<AdminDeliveryListItem> items = result.getContent().stream().map(DeliveryAdminService::listItem).toList();
        return Pagination.of(items, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DeliveryDetailResponse getDelivery(UUID parcelId) {
        Delivery delivery = deliveryRepository.findById(parcelId)
                .orElseThrow(() -> notFound("Delivery not found"));
        return deliveryDetails(delivery);
    }

    @Transactional(readOnly = true)
    public List<CourierWorkloadResponse> listCouriers() {
        return workloadQueries.listWorkloads();
    }

    @Transactional(readOnly = true)
    public Pagination<DeliveryListItem> listCourierDeliveries(UUID courierId, Scope scope, int page, int size) {
        if (!courierRepository.existsById(courierId)) {
            throw notFound("Courier not found");
        }
        Page<DeliveryListItem> result = courierDeliveryQueries.listForCourier(courierId, scope,
                PageRequest.of(page, size));
        return Pagination.of(result.getContent(), page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DispatchDashboardResponse dispatchDashboard() {
        long unassigned = deliveryRepository.countByCourierIdIsNullAndStatus(ParcelStatus.CREATED);
        long inProgress = deliveryRepository.countByStatusIn(IN_PROGRESS_STATUSES);
        long activeCouriers = courierRepository.findAll().stream()
                .filter(courier -> courier.getStatus() == UserStatus.ACTIVE).count();
        return new DispatchDashboardResponse(unassigned, inProgress, activeCouriers);
    }

    @Transactional(readOnly = true)
    public long activeCount(UUID courierId) {
        return workloadQueries.countActiveForCourier(courierId);
    }

    private DeliveryDetailResponse deliveryDetails(Delivery delivery) {
        Courier assignedCourier = delivery.getCourierId() == null ? null
                : courierRepository.findById(delivery.getCourierId()).orElse(null);
        DeliveryDetailResponse.CourierSummary courier = assignedCourier == null ? null
                : new DeliveryDetailResponse.CourierSummary(assignedCourier.getId(), assignedCourier.getFullName(),
                        assignedCourier.getVehicleType(), assignedCourier.getVehicleNumber());
        List<DeliveryAssignment> history = assignmentRepository.findByParcelIdOrderByAssignedAtAsc(delivery.getParcelId());
        Map<UUID, String> courierNames = courierRepository.findAllById(
                        history.stream().map(DeliveryAssignment::getCourierId).distinct().toList())
                .stream().collect(Collectors.toMap(Courier::getId, Courier::getFullName));
        List<DeliveryDetailResponse.AssignmentHistoryItem> assignments = history.stream()
                .map(item -> new DeliveryDetailResponse.AssignmentHistoryItem(item.getCourierId(),
                        courierNames.get(item.getCourierId()), item.getAssignedBy(), item.getAssignedAt(),
                        item.getUnassignedAt()))
                .toList();
        return new DeliveryDetailResponse(delivery.getParcelId(), delivery.getTrackingNumber(), delivery.getStatus(),
                courier, delivery.getAssignedAt(), assignments);
    }

    private static AdminDeliveryListItem listItem(Delivery delivery) {
        return new AdminDeliveryListItem(delivery.getParcelId(), delivery.getTrackingNumber(), delivery.getStatus(),
                delivery.getCourierId(), delivery.getAssignedAt(), delivery.getOriginDistrictCode(),
                delivery.getOriginDistrictName(), delivery.getDestinationDistrictCode(),
                delivery.getDestinationDistrictName(), delivery.getPickupCity(), delivery.getDropoffCity(),
                delivery.getParcelCreatedAt());
    }

    private static Sort resolveSort(String sort, boolean oldestFirst) {
        if (oldestFirst) return Sort.by(Sort.Direction.ASC, "parcelCreatedAt");
        if (sort == null || sort.isBlank()) return Sort.by(Sort.Direction.DESC, "parcelCreatedAt");
        String[] parts = sort.split(",", -1);
        String field = parts[0];
        if (!ALLOWED_SORTS.contains(field) || parts.length > 2) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Invalid sort field");
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 2 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC;
        } catch (IllegalArgumentException exception) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Invalid sort direction");
        }
        return Sort.by(direction, switch (field) {
            case "createdAt" -> "parcelCreatedAt";
            default -> field;
        });
    }

    private static Instant dayStart(LocalDate date) {
        return date.atStartOfDay(COLOMBO).toInstant();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static DomainException notFound(String detail) {
        return new DomainException(ErrorCode.NOT_FOUND, detail);
    }
}