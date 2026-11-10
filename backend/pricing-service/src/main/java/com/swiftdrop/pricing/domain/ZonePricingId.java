package com.swiftdrop.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** SRS 6.3: pricing is directed, identified by the ordered pair of zone ids. */
@Embeddable
public class ZonePricingId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "origin_zone_id", nullable = false)
    private UUID originZoneId;
    @Column(name = "destination_zone_id", nullable = false)
    private UUID destinationZoneId;

    protected ZonePricingId() { }
    public ZonePricingId(UUID originZoneId, UUID destinationZoneId) {
        this.originZoneId = originZoneId;
        this.destinationZoneId = destinationZoneId;
    }
    public UUID getOriginZoneId() { return originZoneId; }
    public UUID getDestinationZoneId() { return destinationZoneId; }
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ZonePricingId that)) return false;
        return Objects.equals(originZoneId, that.originZoneId)
                && Objects.equals(destinationZoneId, that.destinationZoneId);
    }
    @Override
    public int hashCode() { return Objects.hash(originZoneId, destinationZoneId); }
}
