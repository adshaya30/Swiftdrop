package com.swiftdrop.tracking.service;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.entity.TrackingParcel;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory SSE emitter registry for public tracking streams (FR-TRK-003, SRS 3.8).
 * Supports at least 200 concurrent streams (S: 500).
 */
@Service
public class SseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterService.class);
    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final TrackingEventRepository eventRepo;

    public SseEmitterService(TrackingEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    /**
     * Register a new SSE emitter for a tracking number.
     * Registers BEFORE sending the snapshot so no event is lost.
     */
    public SseEmitter register(String trackingNumber, TrackingParcel parcel) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        // Register first, then send snapshot
        emitters.computeIfAbsent(trackingNumber, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Cleanup callbacks
        Runnable cleanup = () -> removeEmitter(trackingNumber, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send snapshot event
        try {
            PublicTrackingResponse snapshot = buildPublicResponse(parcel);
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));

            // If already terminal, complete immediately
            if (ParcelStatus.valueOf(parcel.getStatus()).isTerminal()) {
                emitter.complete();
            }
        } catch (IOException e) {
            log.debug("Failed to send snapshot for {}", trackingNumber);
            removeEmitter(trackingNumber, emitter);
        }

        return emitter;
    }

    /**
     * Push a status update to all SSE emitters for a tracking number.
     * Called after the transaction commits.
     */
    public void sendStatusUpdate(String trackingNumber, TrackingParcel parcel) {
        var list = emitters.get(trackingNumber);
        if (list == null || list.isEmpty()) return;

        PublicTrackingResponse data = buildPublicResponse(parcel);
        boolean isTerminal = ParcelStatus.valueOf(parcel.getStatus()).isTerminal();

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("status").data(data));
                if (isTerminal) {
                    emitter.complete();
                }
            } catch (IOException e) {
                removeEmitter(trackingNumber, emitter);
            }
        }
    }

    /**
     * Send heartbeat comments to all active emitters (called every 25s by the scheduler).
     */
    public void sendHeartbeats() {
        for (var entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    removeEmitter(entry.getKey(), emitter);
                }
            }
        }
    }

    private void removeEmitter(String trackingNumber, SseEmitter emitter) {
        var list = emitters.get(trackingNumber);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(trackingNumber);
            }
        }
    }

    private PublicTrackingResponse buildPublicResponse(TrackingParcel parcel) {
        List<TrackingEvent> events = eventRepo.findByParcelIdOrderBySequenceAsc(parcel.getParcelId());
        return PublicTrackingResponse.from(parcel, events);
    }
}

