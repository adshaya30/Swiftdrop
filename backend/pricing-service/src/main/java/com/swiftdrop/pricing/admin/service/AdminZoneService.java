package com.swiftdrop.pricing.admin.service;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.pricing.admin.dto.CreateZoneRequest;
import com.swiftdrop.pricing.admin.dto.UpdateZoneRequest;
import com.swiftdrop.pricing.admin.dto.ZoneAdminResponse;
import com.swiftdrop.pricing.admin.dto.ZoneAdminResponse.ZoneDistrictDto;
import com.swiftdrop.pricing.domain.District;
import com.swiftdrop.pricing.domain.DistrictRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-PRC-006 zone and district administration. Every change writes its {@code audit.recorded} outbox row in the same
 * transaction (FR-PRC-008). Changes affect future quotes only: parcels snapshot zones and fees (BR-09).
 */
@Service
@Transactional
public class AdminZoneService {

    private final ZoneRepository zones;
    private final DistrictRepository districts;
    private final AuditEvents audit;

    public AdminZoneService(ZoneRepository zones, DistrictRepository districts, AuditEvents audit) {
        this.zones = zones;
        this.districts = districts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ZoneAdminResponse> list() {
        Map<UUID, List<ZoneDistrictDto>> byZone = districts.findAll().stream()
                .sorted(Comparator.comparing(District::getName))
                .collect(Collectors.groupingBy(d -> d.getZone().getId(), LinkedHashMap::new,
                        Collectors.mapping(d -> new ZoneDistrictDto(d.getCode(), d.getName()), Collectors.toList())));
        return zones.findAll(Sort.by("name")).stream()
                .map(z -> toResponse(z, byZone.getOrDefault(z.getId(), List.of())))
                .toList();
    }

    public ZoneAdminResponse create(CreateZoneRequest request) {
        String code = request.code();
        String name = request.name().trim();
        // SRS gap: a duplicate code or name is not specified; answered as 400 VALIDATION_ERROR.
        if (zones.findByCode(code).isPresent()) {
            throw duplicate("code", "A zone with this code already exists");
        }
        if (zones.existsByName(name)) {
            throw duplicate("name", "A zone with this name already exists");
        }
        Zone zone;
        try {
            zone = zones.saveAndFlush(new Zone(code, name, true));
        } catch (DataIntegrityViolationException e) {
            throw duplicate("code", "A zone with this code or name already exists");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("zoneCode", zone.getCode());
        details.put("zoneName", zone.getName());
        audit.record("ZONE_CREATED", "ZONE", zone.getId().toString(), details);
        return toResponse(zone, List.of());
    }

    public ZoneAdminResponse update(UUID id, UpdateZoneRequest request) {
        if (request.name() == null && request.active() == null) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new FieldError("name", "Provide a name or an active flag")));
        }
        Zone zone = zones.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Zone not found"));
        String previousName = zone.getName();
        boolean previousActive = zone.isActive();

        if (request.name() != null) {
            String name = request.name().trim();
            if (!name.equals(zone.getName()) && zones.existsByName(name)) {
                throw duplicate("name", "A zone with this name already exists");
            }
            zone.setName(name);
        }
        if (request.active() != null) {
            // FR-PRC-006: a zone that still has districts cannot be deactivated.
            if (!request.active() && districts.existsByZoneId(zone.getId())) {
                throw new DomainException(ErrorCode.ZONE_IN_USE, "The zone still has districts and cannot be deactivated");
            }
            zone.setActive(request.active());
        }
        try {
            zones.saveAndFlush(zone);
        } catch (DataIntegrityViolationException e) {
            throw duplicate("name", "A zone with this name already exists");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("zoneCode", zone.getCode());
        details.put("previousZoneName", previousName);
        details.put("newZoneName", zone.getName());
        details.put("previousActive", previousActive);
        details.put("newActive", zone.isActive());
        audit.record("ZONE_UPDATED", "ZONE", zone.getId().toString(), details);

        List<ZoneDistrictDto> members = districts.findAll().stream()
                .filter(d -> d.getZone().getId().equals(zone.getId()))
                .sorted(Comparator.comparing(District::getName))
                .map(d -> new ZoneDistrictDto(d.getCode(), d.getName()))
                .toList();
        return toResponse(zone, members);
    }

    /** FR-PRC-006: moves a district to exactly one other zone. Later quotes use the new zone; parcels keep theirs. */
    public ZoneAdminResponse moveDistrict(String districtCode, UUID zoneId) {
        District district = districts.findById(districtCode)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "District not found"));
        Zone target = zones.findById(zoneId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Zone not found"));
        Zone previous = district.getZone();
        if (previous.getId().equals(target.getId())) {
            return responseFor(target);
        }
        // SRS gap: an inactive zone must hold no districts (FR-PRC-006), so moving into one is refused.
        if (!target.isActive()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                    List.of(new FieldError("zoneId", "The target zone is inactive")));
        }
        district.setZone(target);
        districts.saveAndFlush(district);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("districtCode", district.getCode());
        details.put("previousZoneCode", previous.getCode());
        details.put("newZoneCode", target.getCode());
        audit.record("DISTRICT_MOVED", "DISTRICT", district.getCode(), details);
        return responseFor(target);
    }

    private ZoneAdminResponse responseFor(Zone zone) {
        List<ZoneDistrictDto> members = districts.findAll().stream()
                .filter(d -> d.getZone().getId().equals(zone.getId()))
                .sorted(Comparator.comparing(District::getName))
                .map(d -> new ZoneDistrictDto(d.getCode(), d.getName()))
                .toList();
        return toResponse(zone, members);
    }

    private static ZoneAdminResponse toResponse(Zone zone, List<ZoneDistrictDto> members) {
        return new ZoneAdminResponse(zone.getId(), zone.getCode(), zone.getName(), zone.isActive(), members);
    }

    private static DomainException duplicate(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed",
                List.of(new FieldError(field, message)));
    }
}
