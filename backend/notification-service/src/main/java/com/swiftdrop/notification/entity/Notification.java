package com.swiftdrop.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Notification record (SRS 6.3 notifications, FR-NTF-004).
 */
@Entity
@Table(name = "notifications",
       uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "channel", "recipient_kind"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "tracking_number", length = 13)
    private String trackingNumber;

    @Column(name = "channel", nullable = false, length = 10)
    private String channel;

    @Column(name = "recipient_kind", nullable = false, length = 10)
    private String recipientKind;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "PENDING";

    @Column(name = "template_code", nullable = false, length = 60)
    private String templateCode;

    @Column(name = "destination_masked", length = 100)
    private String destinationMasked;

    @Column(name = "body_preview", length = 300)
    private String bodyPreview;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "error_summary", length = 200)
    private String errorSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public Notification() {}

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getRecipientKind() { return recipientKind; }
    public void setRecipientKind(String recipientKind) { this.recipientKind = recipientKind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getDestinationMasked() { return destinationMasked; }
    public void setDestinationMasked(String destinationMasked) { this.destinationMasked = destinationMasked; }
    public String getBodyPreview() { return bodyPreview; }
    public void setBodyPreview(String bodyPreview) { this.bodyPreview = bodyPreview; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    @PrePersist
    void onPersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
