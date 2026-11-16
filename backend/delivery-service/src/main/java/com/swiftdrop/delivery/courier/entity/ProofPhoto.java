package com.swiftdrop.delivery.courier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Proof photo binary storage (FR-DEL-006).
 * Loaded lazily only on GET /api/v1/proofs/{parcelId}/photo.
 */
@Entity
@Table(name = "proof_photos")
public class ProofPhoto {

    @Id
    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType = "image/jpeg";

    @Column(name = "data", nullable = false)
    private byte[] data;

    protected ProofPhoto() {
    }

    public ProofPhoto(UUID parcelId, String contentType, byte[] data) {
        this.parcelId = parcelId;
        this.contentType = contentType;
        this.data = data;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }
}

