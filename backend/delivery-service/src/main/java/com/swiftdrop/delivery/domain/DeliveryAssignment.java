package com.swiftdrop.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_assignments")
public class DeliveryAssignment {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    protected DeliveryAssignment() {
    }

    public DeliveryAssignment(UUID id, UUID parcelId, UUID courierId, UUID assignedBy, Instant assignedAt) {
        this.id = id;
        this.parcelId = parcelId;
        this.courierId = courierId;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public void close(Instant unassignedAt) {
        this.unassignedAt = unassignedAt;
    }

    public UUID getId() { return id; }
    public UUID getParcelId() { return parcelId; }
    public UUID getCourierId() { return courierId; }
    public UUID getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getUnassignedAt() { return unassignedAt; }
}