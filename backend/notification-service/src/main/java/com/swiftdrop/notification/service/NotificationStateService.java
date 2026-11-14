package com.swiftdrop.notification.service;

import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Each state transition commits independently of broker acknowledgement and external delivery. */
@Service
public class NotificationStateService {
    private static final String SAFE_ERROR = "Notification delivery failed";
    private final NotificationRepository repository;

    public NotificationStateService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Snapshot load(UUID id) {
        Notification n = repository.findById(id).orElseThrow(NotificationProcessingException::new);
        return new Snapshot(n.getId(), n.getEventId(), n.getChannel(), n.getRecipientKind(), n.getTemplateCode(),
                n.getTrackingNumber(), n.getStatus(), n.getAttempts());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void skipped(UUID id, String maskedDestination) {
        Notification n = repository.findById(id).orElseThrow(NotificationProcessingException::new);
        if (isComplete(n)) return;
        n.setStatus("SKIPPED");
        n.setDestinationMasked(maskedDestination);
        n.setBodyPreview(null);
        n.setErrorSummary(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sent(UUID id, int attempts, String maskedDestination, String smsBody) {
        Notification n = repository.findById(id).orElseThrow(NotificationProcessingException::new);
        if (isComplete(n)) return;
        n.setStatus("SENT");
        n.setAttempts(attempts);
        n.setDestinationMasked(maskedDestination);
        n.setBodyPreview("SMS".equals(n.getChannel()) ? smsBody : null);
        n.setErrorSummary(null);
        n.setSentAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(UUID id, int attempts, String maskedDestination) {
        Notification n = repository.findById(id).orElseThrow(NotificationProcessingException::new);
        if (isComplete(n)) return;
        n.setStatus("FAILED");
        n.setAttempts(attempts);
        n.setDestinationMasked(maskedDestination);
        n.setBodyPreview(null);
        n.setErrorSummary(SAFE_ERROR);
    }

    private static boolean isComplete(Notification n) {
        return "SENT".equals(n.getStatus()) || "SKIPPED".equals(n.getStatus());
    }

    public record Snapshot(UUID id, UUID eventId, String channel, String recipientKind, String templateCode,
                           String trackingNumber, String status, int attempts) {}
}
