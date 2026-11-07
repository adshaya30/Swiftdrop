package com.swiftdrop.auth.users.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a courier's operational profile (vehicle info, status).
 * SRS v2.1 – Section 6.3, FR-AUTH-020
 */
@Entity
@Table(name = "courier_profiles")
public class CourierProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vehicle_type", nullable = false, length = 20)
    private String vehicleType;

    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Transient
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public CourierProfile() {}

    public CourierProfile(UUID userId, String vehicleType, String vehicleNumber, String status) {
        this.userId = userId;
        this.vehicleType = vehicleType;
        this.vehicleNumber = vehicleNumber;
        this.status = status;
    }

    public UUID getId() { return userId; }
    public void setId(UUID id) { this.userId = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
