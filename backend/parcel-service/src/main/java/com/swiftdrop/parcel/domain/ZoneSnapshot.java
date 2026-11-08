package com.swiftdrop.parcel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** BR-09: local value only, never a relationship to the pricing database. */
@Embeddable
public class ZoneSnapshot {
    @Column(length = 20, nullable = false, updatable = false)
    private String code;
    @Column(length = 60, nullable = false, updatable = false)
    private String name;
    protected ZoneSnapshot() { }
    public ZoneSnapshot(String code, String name) {
        this.code = code;
        this.name = name;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
}
