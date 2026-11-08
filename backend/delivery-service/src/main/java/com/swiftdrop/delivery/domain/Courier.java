package com.swiftdrop.delivery.domain;

import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "couriers")
public class Courier {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private UserStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Courier() {
    }

    public Courier(UUID id, String fullName, VehicleType vehicleType, String vehicleNumber, UserStatus status) {
        this.id = id;
        this.fullName = fullName;
        this.vehicleType = vehicleType;
        this.vehicleNumber = vehicleNumber;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void update(String fullName, VehicleType vehicleType, String vehicleNumber, UserStatus status) {
        this.fullName = fullName;
        this.vehicleType = vehicleType;
        this.vehicleNumber = vehicleNumber;
        this.status = status;
    }

    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public VehicleType getVehicleType() { return vehicleType; }
    public String getVehicleNumber() { return vehicleNumber; }
    public UserStatus getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}