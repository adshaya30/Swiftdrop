package com.swiftdrop.tracking.controller;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.util.TrackingNumberFormat;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.entity.TrackingParcel;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import com.swiftdrop.tracking.repository.TrackingParcelRepository;
import com.swiftdrop.tracking.service.SseEmitterService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Public tracking API – no authentication required (FR-TRK-002, FR-TRK-003).
 * Malformed and unknown tracking numbers both return 404 NOT_FOUND, never 400.
 */
@RestController
@RequestMapping("/api/v1/public/tracking")
public class PublicTrackingController {

    private final TrackingParcelRepository parcelRepo;
    private final TrackingEventRepository eventRepo;
    private final SseEmitterService sseEmitterService;

    public PublicTrackingController(TrackingParcelRepository parcelRepo,
                                    TrackingEventRepository eventRepo,
                                    SseEmitterService sseEmitterService) {
        this.parcelRepo = parcelRepo;
        this.eventRepo = eventRepo;
        this.sseEmitterService = sseEmitterService;
    }

    /**
     * FR-TRK-002: GET /api/v1/public/tracking/{trackingNumber}
     * Returns public tracking view with Cache-Control: no-store.
     * Malformed and unknown numbers both return 404 (never 400).
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<PublicTrackingResponse> getPublicTracking(
            @PathVariable String trackingNumber) {

        // Malformed tracking numbers return 404, not 400 (SRS requirement)
        if (!TrackingNumberFormat.isValid(trackingNumber)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Parcel not found");
        }

        TrackingParcel parcel = parcelRepo.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));

        List<TrackingEvent> events = eventRepo.findByParcelIdOrderBySequenceAsc(parcel.getParcelId());
        PublicTrackingResponse response = PublicTrackingResponse.from(parcel, events);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    /**
     * FR-TRK-003: GET /api/v1/public/tracking/{trackingNumber}/stream (SSE)
     * 404 before the stream starts for unknown or malformed numbers.
     * Cache-Control: no-store, X-Accel-Buffering: no.
     */
    @GetMapping(value = "/{trackingNumber}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamTracking(@PathVariable String trackingNumber) {
        // 404 before the stream starts for malformed numbers
        if (!TrackingNumberFormat.isValid(trackingNumber)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Parcel not found");
        }

        TrackingParcel parcel = parcelRepo.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));

        // Register emitter and send snapshot (FR-TRK-003)
        SseEmitter emitter = sseEmitterService.register(trackingNumber, parcel);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
}

