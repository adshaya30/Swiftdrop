package com.swiftdrop.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import com.swiftdrop.common.enums.ParcelSize;

/** SRS 6.3 size_surcharges persistence model; never serialize entities as API responses. */
@Entity
@Table(name = "size_surcharges")
public class SizeSurcharge {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ParcelSize size;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal surcharge;

    @Column(nullable = false, length = 80)
    private String description;

    protected SizeSurcharge() {
    }

    public SizeSurcharge(ParcelSize size, BigDecimal surcharge, String description) {
        this.size = size;
        this.surcharge = surcharge;
        this.description = description;
    }

    public ParcelSize getSize() { return size; }
    public BigDecimal getSurcharge() { return surcharge; }
    public void setSurcharge(BigDecimal surcharge) { this.surcharge = surcharge; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }


}
