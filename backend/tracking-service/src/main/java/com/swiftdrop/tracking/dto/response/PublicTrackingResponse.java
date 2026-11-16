package com.swiftdrop.tracking.dto.response;

import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.entity.TrackingParcel;
import java.time.Instant;
import java.util.List;

/**
 * FR-TRK-002: Public tracking view. Contains ONLY the allowed fields (BR-20):
 * no names, phones, emails, addresses, notes, proof, courier identity, fees or merchant data.
 */
public record PublicTrackingResponse(
        String trackingNumber,
        String status,
        Instant createdAt,
        Instant updatedAt,
        LocationResponse origin,
        LocationResponse destination,
        List<HistoryEntry> history) {

    public record LocationResponse(String districtName, String zoneName) {}

    public record HistoryEntry(String status, Instant at, String failureReasonLabel) {}

    public static PublicTrackingResponse from(TrackingParcel parcel, List<TrackingEvent> events) {
        var origin = new LocationResponse(parcel.getOriginDistrictName(), parcel.getOriginZoneName());
        var dest = new LocationResponse(parcel.getDestinationDistrictName(), parcel.getDestinationZoneName());
        var history = events.stream()
                .map(e -> new HistoryEntry(
                        e.getToStatus(),
                        e.getOccurredAt(),
                        failureLabel(e.getFailureReasonCode())))
                .toList();
        return new PublicTrackingResponse(
                parcel.getTrackingNumber(), parcel.getStatus(),
                parcel.getCreatedAt(), parcel.getUpdatedAt(),
                origin, dest, history);
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

