package com.swiftdrop.delivery.messaging;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.common.events.ProcessedEvents;
import com.swiftdrop.common.events.RetryableEventException;
import com.swiftdrop.common.events.data.CourierChangedData;
import com.swiftdrop.common.events.data.DeliveryUnassignedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.delivery.domain.Courier;
import com.swiftdrop.delivery.domain.CourierRepository;
import com.swiftdrop.delivery.domain.Delivery;
import com.swiftdrop.delivery.domain.DeliveryAssignmentRepository;
import com.swiftdrop.delivery.domain.DeliveryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryEventHandler {

    private final ProcessedEvents processedEvents;
    private final DeliveryRepository deliveryRepository;
    private final CourierRepository courierRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    public DeliveryEventHandler(ProcessedEvents processedEvents, DeliveryRepository deliveryRepository,
                                CourierRepository courierRepository,
                                DeliveryAssignmentRepository assignmentRepository,
                                OutboxService outboxService, Clock clock) {
        this.processedEvents = processedEvents;
        this.deliveryRepository = deliveryRepository;
        this.courierRepository = courierRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional
    public void handle(EventEnvelope<?> event) {
        // SRS 3.5: this insert participates in the same transaction as the event's business changes.
        if (!processedEvents.firstTime(event.eventId())) {
            return;
        }
        if (EventTypes.PARCEL_CREATED.equals(event.eventType()) && event.data() instanceof ParcelCreatedData data) {
            createDelivery(data);
        } else if (EventTypes.PARCEL_STATUS_CHANGED.equals(event.eventType())
                && event.data() instanceof ParcelStatusChangedData data) {
            updateStatus(data);
        } else if (EventTypes.COURIER_CHANGED.equals(event.eventType())
                && event.data() instanceof CourierChangedData data) {
            upsertCourier(data);
        } else {
            throw new IllegalArgumentException("Unsupported event payload for delivery.events");
        }
    }

    private void createDelivery(ParcelCreatedData data) {
        // FR-DEL-001: one unassigned delivery projection per parcel, initialized at lifecycle sequence 1.
        if (deliveryRepository.existsById(data.parcelId())) {
            return;
        }
        deliveryRepository.save(new Delivery(data.parcelId(), data.trackingNumber(), ParcelStatus.CREATED, 1,
                data.ownerType(), data.customerId(), data.merchantId(), data.originDistrictCode(),
                data.originDistrictName(), data.destinationDistrictCode(), data.destinationDistrictName(),
                data.pickupCity(), data.dropoffCity(), data.createdAt()));
    }

    private void updateStatus(ParcelStatusChangedData data) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(data.parcelId())
                .orElseThrow(() -> new RetryableEventException("Parcel delivery is not known yet"));
        if (data.sequence() <= delivery.getStatusSequence()) {
            return;
        }
        delivery.updateStatus(data.status(), data.sequence(), data.statusChangedAt());
    }

    private void upsertCourier(CourierChangedData data) {
        Courier courier = courierRepository.findById(data.courierId()).orElse(null);
        if (courier == null) {
            courierRepository.save(new Courier(data.courierId(), data.fullName(), data.vehicleType(),
                    data.vehicleNumber(), data.status()));
        } else {
            courier.update(data.fullName(), data.vehicleType(), data.vehicleNumber(), data.status());
        }
        if (data.status() == UserStatus.DEACTIVATED) {
            unassignCreatedDeliveries(data.courierId());
        }
    }

    private void unassignCreatedDeliveries(UUID courierId) {
        // FR-DEL-010: lock and touch only CREATED deliveries; in-progress parcels remain assigned.
        List<Delivery> createdDeliveries = deliveryRepository.findByCourierIdAndStatusForUpdate(
                courierId, ParcelStatus.CREATED);
        for (Delivery delivery : createdDeliveries) {
            Instant unassignedAt = clock.instant();
            delivery.unassign();
            assignmentRepository.findByParcelIdAndUnassignedAtIsNull(delivery.getParcelId())
                    .ifPresent(assignment -> assignment.close(unassignedAt));
            outboxService.add(EventTypes.DELIVERY_UNASSIGNED,
                    new DeliveryUnassignedData(delivery.getParcelId(), unassignedAt,
                            DeliveryUnassignedData.REASON_COURIER_DEACTIVATED));
        }
    }
}