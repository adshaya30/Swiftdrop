package com.swiftdrop.pricing.admin.controller;

import com.swiftdrop.pricing.admin.dto.CreateZoneRequest;
import com.swiftdrop.pricing.admin.dto.MoveDistrictRequest;
import com.swiftdrop.pricing.admin.dto.UpdateZoneRequest;
import com.swiftdrop.pricing.admin.dto.ZoneAdminResponse;
import com.swiftdrop.pricing.admin.service.AdminZoneService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-PRC-006: zones and districts. ADMIN only; client-credentials tokens are refused by common-lib (SRS 3.6). */
@RestController
@Validated
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminZoneController {

    private final AdminZoneService service;

    public AdminZoneController(AdminZoneService service) {
        this.service = service;
    }

    @GetMapping("/zones")
    @Operation(summary = "List zones with their districts", description = "FR-PRC-006")
    public List<ZoneAdminResponse> list() {
        return service.list();
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a zone", description = "FR-PRC-006, FR-PRC-008: code [A-Z_]{2,20}, immutable. "
            + "Audited (ZONE_CREATED).")
    public ZoneAdminResponse create(@Valid @RequestBody CreateZoneRequest body) {
        return service.create(body);
    }

    @PatchMapping("/zones/{id}")
    @Operation(summary = "Rename or (de)activate a zone", description = "FR-PRC-006: a zone that still has districts "
            + "cannot be deactivated (409 ZONE_IN_USE). Audited (ZONE_UPDATED).")
    public ZoneAdminResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateZoneRequest body) {
        return service.update(id, body);
    }

    @PutMapping("/districts/{districtCode}/zone")
    @Operation(summary = "Move a district to a zone", description = "FR-PRC-006, BR-09: affects later quotes only. "
            + "Audited (DISTRICT_MOVED).")
    public ZoneAdminResponse moveDistrict(@PathVariable @Size(max = 20) String districtCode,
                                          @Valid @RequestBody MoveDistrictRequest body) {
        return service.moveDistrict(districtCode, body.zoneId());
    }
}
