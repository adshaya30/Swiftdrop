package com.swiftdrop.delivery.domain;

import com.swiftdrop.common.enums.ParcelStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class CourierDeliveryQueries {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final List<ParcelStatus> ACTIVE_STATUSES = List.of(
            ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
    private static final List<ParcelStatus> TERMINAL_STATUSES = List.of(
            ParcelStatus.DELIVERED, ParcelStatus.FAILED, ParcelStatus.CANCELLED);
    private static final String STATUS_ORDER = """
            order by case delivery.status
                when :created then 0
                when :pickedUp then 1
                when :inTransit then 2
                when :outForDelivery then 3
                when :delivered then 4
                when :failed then 5
                when :cancelled then 6
                else 7
            end, delivery.assignedAt asc
            """;

    @PersistenceContext
    private EntityManager entityManager;

    private final Clock clock;

    public CourierDeliveryQueries(Clock clock) {
        this.clock = clock;
    }

    public Page<DeliveryListItem> listForCourier(UUID courierId, Scope scope, Pageable pageable) {
        // FR-DEL-003 and BR-18: active work remains visible; terminal work is scoped to the Colombo calendar day.
        String scopePredicate = switch (scope) {
            case TODAY -> "(delivery.status in :activeStatuses or (delivery.status in :terminalStatuses "
                    + "and delivery.terminalAt >= :dayStart and delivery.terminalAt < :nextDayStart))";
            case ACTIVE -> "delivery.status in :activeStatuses";
            case COMPLETED -> "delivery.status in :terminalStatuses";
        };
        String projection = "select new com.swiftdrop.delivery.domain.DeliveryListItem(" +
                "delivery.parcelId, delivery.trackingNumber, delivery.status, delivery.pickupCity, " +
                "delivery.dropoffCity, delivery.originDistrictName, delivery.destinationDistrictName, " +
                "delivery.assignedAt) from Delivery delivery where delivery.courierId = :courierId and ";
        TypedQuery<DeliveryListItem> query = entityManager.createQuery(
                projection + scopePredicate + " " + STATUS_ORDER, DeliveryListItem.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "select count(delivery) from Delivery delivery where delivery.courierId = :courierId and "
                        + scopePredicate,
                Long.class);
        bindScope(query, courierId, scope);
        bindScope(countQuery, courierId, scope);
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(query.getResultList(), pageable, countQuery.getSingleResult());
    }

    private void bindScope(TypedQuery<?> query, UUID courierId, Scope scope) {
        query.setParameter("courierId", courierId);
        if (scope != Scope.COMPLETED) {
            query.setParameter("activeStatuses", ACTIVE_STATUSES);
        }
        if (scope != Scope.ACTIVE) {
            query.setParameter("terminalStatuses", TERMINAL_STATUSES);
        }
        if (scope == Scope.TODAY) {
            LocalDate today = LocalDate.now(clock.withZone(COLOMBO));
            Instant dayStart = today.atStartOfDay(COLOMBO).toInstant();
            Instant nextDayStart = today.plusDays(1).atStartOfDay(COLOMBO).toInstant();
            query.setParameter("dayStart", dayStart);
            query.setParameter("nextDayStart", nextDayStart);
        }
        if (query.getParameters().stream().anyMatch(parameter -> parameter.getName() != null
                && parameter.getName().equals("created"))) {
            Map<String, ParcelStatus> statuses = Map.of(
                    "created", ParcelStatus.CREATED,
                    "pickedUp", ParcelStatus.PICKED_UP,
                    "inTransit", ParcelStatus.IN_TRANSIT,
                    "outForDelivery", ParcelStatus.OUT_FOR_DELIVERY,
                    "delivered", ParcelStatus.DELIVERED,
                    "failed", ParcelStatus.FAILED,
                    "cancelled", ParcelStatus.CANCELLED);
            statuses.forEach(query::setParameter);
        }
    }
}