package com.swiftdrop.delivery.domain;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 13)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParcelStatus status;

    @Column(name = "status_sequence", nullable = false)
    private int statusSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 10)
    private OwnerType ownerType;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "origin_district_code", nullable = false, length = 20)
    private String originDistrictCode;

    @Column(name = "origin_district_name", nullable = false, length = 40)
    private String originDistrictName;

    @Column(name = "destination_district_code", nullable = false, length = 20)
    private String destinationDistrictCode;

    @Column(name = "destination_district_name", nullable = false, length = 40)
    private String destinationDistrictName;

    @Column(name = "pickup_city", nullable = false, length = 80)
    private String pickupCity;

    @Column(name = "dropoff_city", nullable = false, length = 80)
    private String dropoffCity;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "parcel_created_at", nullable = false)
    private Instant parcelCreatedAt;

    @Column(name = "terminal_at")
    private Instant terminalAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Delivery() {
    }

    public Delivery(UUID parcelId, String trackingNumber, ParcelStatus status, int statusSequence, OwnerType ownerType,
                    UUID customerId, UUID merchantId, String originDistrictCode, String originDistrictName,
                    String destinationDistrictCode, String destinationDistrictName, String pickupCity,
                    String dropoffCity, Instant parcelCreatedAt) {
        this.parcelId = parcelId;
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.statusSequence = statusSequence;
        this.ownerType = ownerType;
        this.customerId = customerId;
        this.merchantId = merchantId;
        this.originDistrictCode = originDistrictCode;
        this.originDistrictName = originDistrictName;
        this.destinationDistrictCode = destinationDistrictCode;
        this.destinationDistrictName = destinationDistrictName;
        this.pickupCity = pickupCity;
        this.dropoffCity = dropoffCity;
        this.parcelCreatedAt = parcelCreatedAt;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    public void assignTo(UUID newCourierId, Instant assignedAt) {
        this.courierId = newCourierId;
        this.assignedAt = assignedAt;
    }

    public void unassign() {
        this.courierId = null;
        this.assignedAt = null;
    }

    public void updateStatus(ParcelStatus status, int sequence, Instant changedAt) {
        this.status = status;
        this.statusSequence = sequence;
        this.terminalAt = status.isTerminal() ? changedAt : null;
    }

    public UUID getParcelId() { return parcelId; }
    public String getTrackingNumber() { return trackingNumber; }
    public ParcelStatus getStatus() { return status; }
    public int getStatusSequence() { return statusSequence; }
    public OwnerType getOwnerType() { return ownerType; }
    public UUID getCustomerId() { return customerId; }
    public UUID getMerchantId() { return merchantId; }
    public String getOriginDistrictCode() { return originDistrictCode; }
    public String getOriginDistrictName() { return originDistrictName; }
    public String getDestinationDistrictCode() { return destinationDistrictCode; }
    public String getDestinationDistrictName() { return destinationDistrictName; }
    public String getPickupCity() { return pickupCity; }
    public String getDropoffCity() { return dropoffCity; }
    public UUID getCourierId() { return courierId; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getParcelCreatedAt() { return parcelCreatedAt; }
    public Instant getTerminalAt() { return terminalAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}