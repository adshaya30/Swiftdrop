package com.swiftdrop.delivery.courier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Proof of delivery (FR-DEL-007, BR-06).
 * Written only for DELIVERED and never modified after writing.
 */
@Entity
@Table(name = "delivery_proofs")
public class DeliveryProof {

    @Id
    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "has_photo", nullable = false)
    private boolean hasPhoto;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected DeliveryProof() {
    }

    public DeliveryProof(UUID parcelId, String recipientName, String note, boolean hasPhoto, Instant capturedAt) {
        this.parcelId = parcelId;
        this.recipientName = recipientName;
        this.note = note;
        this.hasPhoto = hasPhoto;
        this.capturedAt = capturedAt;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getNote() {
        return note;
    }

    public boolean isHasPhoto() {
        return hasPhoto;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}

