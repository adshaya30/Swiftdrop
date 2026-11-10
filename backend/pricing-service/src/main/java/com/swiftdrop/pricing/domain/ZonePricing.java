package com.swiftdrop.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** SRS 6.3 zone_pricing persistence model; never serialize entities as API responses. */
@Entity
@Table(name = "zone_pricing")
public class ZonePricing {
    @EmbeddedId
    private ZonePricingId id;

    @MapsId("originZoneId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_zone_id", nullable = false)
    private Zone originZone;

    @MapsId("destinationZoneId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_zone_id", nullable = false)
    private Zone destinationZone;

    @Column(name = "base_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "per_kg_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal perKgFee;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ZonePricing() {
    }

    public ZonePricing(Zone originZone, Zone destinationZone, BigDecimal baseFee, BigDecimal perKgFee) {
        this.id = new ZonePricingId(originZone.getId(), destinationZone.getId());
        this.originZone = originZone;
        this.destinationZone = destinationZone;
        this.baseFee = baseFee;
        this.perKgFee = perKgFee;
    }

    public ZonePricingId getId() { return id; }
    public Zone getOriginZone() { return originZone; }
    public Zone getDestinationZone() { return destinationZone; }
    public BigDecimal getBaseFee() { return baseFee; }
    public void setBaseFee(BigDecimal baseFee) { this.baseFee = baseFee; }
    public BigDecimal getPerKgFee() { return perKgFee; }
    public void setPerKgFee(BigDecimal perKgFee) { this.perKgFee = perKgFee; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    private void onInsert() {
        id = new ZonePricingId(originZone.getId(), destinationZone.getId());
        updatedAt = Instant.now();
    }
    @PreUpdate
    private void onUpdate() { updatedAt = Instant.now(); }
}
