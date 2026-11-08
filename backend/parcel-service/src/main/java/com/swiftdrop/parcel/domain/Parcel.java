package com.swiftdrop.parcel.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.common.enums.ParcelStatus;

/** SRS 6.3 parcels persistence model; never serialize entities as API responses. */
@Entity
@Table(name = "parcels")
public class Parcel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 13, updatable = false)
    private String trackingNumber;

    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "merchant_id", updatable = false)
    private UUID merchantId;

    @Column(name = "merchant_reference", length = 64, updatable = false)
    private String merchantReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParcelStatus status = ParcelStatus.CREATED;

    @Column(name = "status_sequence", nullable = false)
    private int statusSequence = 1;

    @Embedded
    private PickupDetails pickup;

    @Embedded
    private DropoffDetails dropoff;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ParcelSize size;

    @Column(length = 200, updatable = false)
    private String description;

    @Column(name = "delivery_instructions", length = 300, updatable = false)
    private String deliveryInstructions;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "code", column = @Column(name = "origin_zone_code", length = 20, nullable = false, updatable = false)),
        @AttributeOverride(name = "name", column = @Column(name = "origin_zone_name", length = 60, nullable = false, updatable = false))
    })
    private ZoneSnapshot originZone;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "code", column = @Column(name = "destination_zone_code", length = 20, nullable = false, updatable = false)),
        @AttributeOverride(name = "name", column = @Column(name = "destination_zone_name", length = 60, nullable = false, updatable = false))
    })
    private ZoneSnapshot destinationZone;

    @Column(name = "fee_base", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal feeBase;

    @Column(name = "fee_weight", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal feeWeight;

    @Column(name = "fee_size", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal feeSize;

    @Column(name = "fee_discount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal feeDiscount;

    @Column(name = "fee_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal feeTotal;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)", updatable = false)
    private String currency;

    @Column(name = "terminal_at")
    private Instant terminalAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Parcel() {
    }

    public Parcel(String trackingNumber, UUID customerId, UUID merchantId, String merchantReference, PickupDetails pickup, DropoffDetails dropoff, BigDecimal weightKg, ParcelSize size, String description, String deliveryInstructions, ZoneSnapshot originZone, ZoneSnapshot destinationZone, BigDecimal feeBase, BigDecimal feeWeight, BigDecimal feeSize, BigDecimal feeDiscount, BigDecimal feeTotal, String currency) {
        this.trackingNumber = trackingNumber;
        this.customerId = customerId;
        this.merchantId = merchantId;
        this.merchantReference = merchantReference;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.weightKg = weightKg;
        this.size = size;
        this.description = description;
        this.deliveryInstructions = deliveryInstructions;
        this.originZone = originZone;
        this.destinationZone = destinationZone;
        this.feeBase = feeBase;
        this.feeWeight = feeWeight;
        this.feeSize = feeSize;
        this.feeDiscount = feeDiscount;
        this.feeTotal = feeTotal;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public String getTrackingNumber() { return trackingNumber; }
    public UUID getCustomerId() { return customerId; }
    public UUID getMerchantId() { return merchantId; }
    public String getMerchantReference() { return merchantReference; }
    public ParcelStatus getStatus() { return status; }
    public void setStatus(ParcelStatus status) { this.status = status; }
    public int getStatusSequence() { return statusSequence; }
    public void setStatusSequence(int statusSequence) { this.statusSequence = statusSequence; }
    public PickupDetails getPickup() { return pickup; }
    public DropoffDetails getDropoff() { return dropoff; }
    public BigDecimal getWeightKg() { return weightKg; }
    public ParcelSize getSize() { return size; }
    public String getDescription() { return description; }
    public String getDeliveryInstructions() { return deliveryInstructions; }
    public ZoneSnapshot getOriginZone() { return originZone; }
    public ZoneSnapshot getDestinationZone() { return destinationZone; }
    public BigDecimal getFeeBase() { return feeBase; }
    public BigDecimal getFeeWeight() { return feeWeight; }
    public BigDecimal getFeeSize() { return feeSize; }
    public BigDecimal getFeeDiscount() { return feeDiscount; }
    public BigDecimal getFeeTotal() { return feeTotal; }
    public String getCurrency() { return currency; }
    public Instant getTerminalAt() { return terminalAt; }
    public void setTerminalAt(Instant terminalAt) { this.terminalAt = terminalAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    @PrePersist
    private void onInsert() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
