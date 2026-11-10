package com.swiftdrop.tracking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracking projection of a parcel (SRS 6.3 tracking_parcels).
 */
@Entity
@Table(name = "tracking_parcels")
public class TrackingParcel {

    @Id
    @Column(name = "parcel_id")
    private UUID parcelId;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 13)
    private String trackingNumber;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "status_sequence", nullable = false)
    private int statusSequence;

    @Column(name = "owner_type", nullable = false, length = 10)
    private String ownerType;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "origin_district_name", nullable = false, length = 100)
    private String originDistrictName;

    @Column(name = "origin_zone_name", nullable = false, length = 100)
    private String originZoneName;

    @Column(name = "destination_district_name", nullable = false, length = 100)
    private String destinationDistrictName;

    @Column(name = "destination_zone_name", nullable = false, length = 100)
    private String destinationZoneName;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "courier_first_name", length = 50)
    private String courierFirstName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrackingParcel() {}

    public UUID getParcelId() { return parcelId; }
    public void setParcelId(UUID parcelId) { this.parcelId = parcelId; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getStatusSequence() { return statusSequence; }
    public void setStatusSequence(int statusSequence) { this.statusSequence = statusSequence; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public String getOriginDistrictName() { return originDistrictName; }
    public void setOriginDistrictName(String originDistrictName) { this.originDistrictName = originDistrictName; }
    public String getOriginZoneName() { return originZoneName; }
    public void setOriginZoneName(String originZoneName) { this.originZoneName = originZoneName; }
    public String getDestinationDistrictName() { return destinationDistrictName; }
    public void setDestinationDistrictName(String destinationDistrictName) { this.destinationDistrictName = destinationDistrictName; }
    public String getDestinationZoneName() { return destinationZoneName; }
    public void setDestinationZoneName(String destinationZoneName) { this.destinationZoneName = destinationZoneName; }
    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID courierId) { this.courierId = courierId; }
    public String getCourierFirstName() { return courierFirstName; }
    public void setCourierFirstName(String courierFirstName) { this.courierFirstName = courierFirstName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

