package com.swiftdrop.pricing.admin.service;

import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.pricing.admin.dto.PricingRowResponse;
import com.swiftdrop.pricing.admin.dto.PricingUpdateRequest;
import com.swiftdrop.pricing.admin.dto.SizeSurchargeResponse;
import com.swiftdrop.pricing.admin.dto.SizeSurchargeUpdateItem;
import com.swiftdrop.pricing.domain.SizeSurcharge;
import com.swiftdrop.pricing.domain.SizeSurchargeRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZonePricing;
import com.swiftdrop.pricing.domain.ZonePricingId;
import com.swiftdrop.pricing.domain.ZonePricingRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-PRC-007 pricing matrix and size surcharges. Changes affect future quotes only: parcels snapshot their fee
 * components at creation (BR-09). Every change writes an {@code audit.recorded} outbox row in the same transaction
 * (FR-PRC-008).
 */
@Service
@Transactional
public class AdminPricingService {

    private final ZoneRepository zones;
    private final ZonePricingRepository prices;
    private final SizeSurchargeRepository sizes;
    private final AuditEvents audit;

    public AdminPricingService(ZoneRepository zones, ZonePricingRepository prices, SizeSurchargeRepository sizes,
                               AuditEvents audit) {
        this.zones = zones;
        this.prices = prices;
        this.sizes = sizes;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PricingRowResponse> matrix() {
        return prices.findAll().stream()
                .map(AdminPricingService::toRow)
                .sorted(Comparator.comparing(PricingRowResponse::originZoneCode)
                        .thenComparing(PricingRowResponse::destinationZoneCode))
                .toList();
    }

    /** Creates the directed pair if it is missing (FR-PRC-007). */
    public PricingRowResponse upsert(UUID originZoneId, UUID destinationZoneId, PricingUpdateRequest request) {
        Zone origin = zones.findById(originZoneId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Zone not found"));
        Zone destination = zones.findById(destinationZoneId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Zone not found"));
        BigDecimal baseFee = request.baseFee().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal perKgFee = request.perKgFee().setScale(2, RoundingMode.UNNECESSARY);

        ZonePricing row = prices.findById(new ZonePricingId(originZoneId, destinationZoneId)).orElse(null);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("originZoneCode", origin.getCode());
        details.put("destinationZoneCode", destination.getCode());
        if (row == null) {
            row = new ZonePricing(origin, destination, baseFee, perKgFee);
        } else {
            details.put("previousBaseFee", row.getBaseFee());
            details.put("previousPerKgFee", row.getPerKgFee());
            row.setBaseFee(baseFee);
            row.setPerKgFee(perKgFee);
        }
        details.put("newBaseFee", baseFee);
        details.put("newPerKgFee", perKgFee);
        ZonePricing saved = prices.saveAndFlush(row);
        audit.record("PRICING_UPDATED", "ZONE_PRICING", originZoneId + ":" + destinationZoneId, details);
        return toRow(saved);
    }

    @Transactional(readOnly = true)
    public List<SizeSurchargeResponse> surcharges() {
        return sizes.findAll().stream()
                .sorted(Comparator.comparing(SizeSurcharge::getSize))
                .map(s -> new SizeSurchargeResponse(s.getSize(), s.getSurcharge(), s.getDescription()))
                .toList();
    }

    /** SRS gap: body is a list of {size, surcharge} holding exactly the three sizes once each. */
    public List<SizeSurchargeResponse> updateSurcharges(List<SizeSurchargeUpdateItem> items) {
        Set<ParcelSize> seen = EnumSet.noneOf(ParcelSize.class);
        for (SizeSurchargeUpdateItem item : items) {
            if (!seen.add(item.size())) {
                throw invalid("Each size may appear only once");
            }
        }
        if (!seen.equals(EnumSet.allOf(ParcelSize.class))) {
            throw invalid("Provide a surcharge for each of SMALL, MEDIUM and LARGE");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        for (SizeSurchargeUpdateItem item : items) {
            SizeSurcharge entity = sizes.findById(item.size())
                    .orElseThrow(() -> new IllegalStateException("Required size reference data is missing"));
            BigDecimal value = item.surcharge().setScale(2, RoundingMode.UNNECESSARY);
            details.put("previous" + label(item.size()) + "Surcharge", entity.getSurcharge());
            details.put("new" + label(item.size()) + "Surcharge", value);
            entity.setSurcharge(value);
            sizes.save(entity);
        }
        sizes.flush();
        audit.record("SURCHARGES_UPDATED", "SIZE_SURCHARGE", "SIZE_SURCHARGES", details);
        return surcharges();
    }

    private static String label(ParcelSize size) {
        String s = size.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                List.of(new FieldError("size", message)));
    }

    private static PricingRowResponse toRow(ZonePricing p) {
        return new PricingRowResponse(p.getOriginZone().getId(), p.getOriginZone().getCode(),
                p.getDestinationZone().getId(), p.getDestinationZone().getCode(), p.getBaseFee(), p.getPerKgFee());
    }
}
