package com.swiftdrop.tracking.controller;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.tracking.dto.response.TimelineResponse;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.entity.TrackingParcel;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import com.swiftdrop.tracking.repository.TrackingParcelRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * FR-TRK-004: Authenticated timeline endpoint.
 * Role-based access: CUSTOMER (own), MERCHANT (by mid), COURIER (assigned), ADMIN (any).
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TimelineController {

    private final TrackingParcelRepository parcelRepo;
    private final TrackingEventRepository eventRepo;

    public TimelineController(TrackingParcelRepository parcelRepo,
                               TrackingEventRepository eventRepo) {
        this.parcelRepo = parcelRepo;
        this.eventRepo = eventRepo;
    }

    @GetMapping("/parcels/{parcelId}/timeline")
    public ResponseEntity<TimelineResponse> getTimeline(
            @PathVariable UUID parcelId,
            @AuthenticationPrincipal Jwt jwt) {

        CurrentUser user = CurrentUsers.from(jwt);
        String role = user.role();

        TrackingParcel parcel = parcelRepo.findByParcelId(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));

        // Role-based ownership check (FR-TRK-004)
        switch (role) {
            case "CUSTOMER" -> {
                if (parcel.getCustomerId() == null ||
                        !parcel.getCustomerId().equals(user.userId())) {
                    throw new DomainException(ErrorCode.NOT_FOUND, "Parcel not found");
                }
            }
            case "MERCHANT" -> {
                if (parcel.getMerchantId() == null ||
                        !parcel.getMerchantId().equals(user.merchantId())) {
                    throw new DomainException(ErrorCode.NOT_FOUND, "Parcel not found");
                }
            }
            case "COURIER" -> {
                if (parcel.getCourierId() == null ||
                        !parcel.getCourierId().equals(user.userId())) {
                    throw new DomainException(ErrorCode.NOT_FOUND, "Parcel not found");
                }
            }
            case "ADMIN" -> { /* admin can see any parcel */ }
            default -> throw new DomainException(ErrorCode.ACCESS_DENIED, "Access denied");
        }

        List<TrackingEvent> events = eventRepo.findByParcelIdOrderBySequenceAsc(parcelId);

        // Visibility rules:
        // Customer/Merchant: courierFirstName visible, courierId hidden
        // Admin: both visible
        // Courier: neither visible
        boolean showCourierName = "CUSTOMER".equals(role) || "MERCHANT".equals(role) || "ADMIN".equals(role);
        boolean showCourierId = "ADMIN".equals(role);

        TimelineResponse response = TimelineResponse.from(parcel, events, showCourierName, showCourierId);
        return ResponseEntity.ok(response);
    }
}

