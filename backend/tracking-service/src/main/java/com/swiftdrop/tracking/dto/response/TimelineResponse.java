package com.swiftdrop.tracking.dto.response;

import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.entity.TrackingParcel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FR-TRK-004: Authenticated timeline view with role-based field visibility.
 */
public record TimelineResponse(
        UUID parcelId,
        String trackingNumber,
        String status,
        String courierFirstName,
        UUID courierId,
        List<TimelineEntry> history) {

    public record TimelineEntry(
            int sequence,
            String fromStatus,
            String toStatus,
            Instant at,
            String actorRole,
            String note,
            String failureReasonCode,
            String failureReasonLabel) {}

    /**
     * Build timeline with role-based field visibility:
     * - Customer/Merchant: see courierFirstName but not courierId
     * - Admin: sees both courierFirstName and courierId
     * - Courier: sees neither courierFirstName nor courierId
     */
    public static TimelineResponse from(TrackingParcel parcel, List<TrackingEvent> events,
                                         boolean showCourierName, boolean showCourierId) {
        var history = events.stream()
                .map(e -> new TimelineEntry(
                        e.getSequence(),
                        e.getFromStatus(),
                        e.getToStatus(),
                        e.getOccurredAt(),
                        e.getActorRole(),
                        e.getNote(),
                        e.getFailureReasonCode(),
                        failureLabel(e.getFailureReasonCode())))
                .toList();
        return new TimelineResponse(
                parcel.getParcelId(),
                parcel.getTrackingNumber(),
                parcel.getStatus(),
                showCourierName ? parcel.getCourierFirstName() : null,
                showCourierId ? parcel.getCourierId() : null,
                history);
    }

    private static String failureLabel(String code) {
        if (code == null) return null;
        try {
            return FailureReason.valueOf(code).label();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

