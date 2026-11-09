package com.swiftdrop.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** SRS 6.3 districts persistence model; never serialize entities as API responses. */
@Entity
@Table(name = "districts")
public class District {
    @Id
    @Column(length = 20, updatable = false)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    protected District() {
    }

    public District(String code, String name, Zone zone) {
        this.code = code;
        this.name = name;
        this.zone = zone;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }


}
