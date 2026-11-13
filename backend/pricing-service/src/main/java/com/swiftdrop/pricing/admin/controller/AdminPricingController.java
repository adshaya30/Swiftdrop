package com.swiftdrop.pricing.admin.controller;

import com.swiftdrop.pricing.admin.dto.PricingRowResponse;
import com.swiftdrop.pricing.admin.dto.PricingUpdateRequest;
import com.swiftdrop.pricing.admin.dto.SizeSurchargeResponse;
import com.swiftdrop.pricing.admin.dto.SizeSurchargeUpdateItem;
import com.swiftdrop.pricing.admin.service.AdminPricingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-PRC-007: pricing matrix and size surcharges. ADMIN only; client-credentials tokens are refused by common-lib. */
@RestController
@Validated
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {

    private final AdminPricingService service;

    public AdminPricingController(AdminPricingService service) {
        this.service = service;
    }

    @GetMapping("/pricing")
    @Operation(summary = "Full pricing matrix", description = "FR-PRC-007")
    public List<PricingRowResponse> matrix() {
        return service.matrix();
    }

    @PutMapping("/pricing/{originZoneId}/{destinationZoneId}")
    @Operation(summary = "Set the fees of a directed zone pair", description = "FR-PRC-007, BR-09: creates the pair "
            + "if missing; affects future quotes only. Audited (PRICING_UPDATED).")
    public PricingRowResponse upsert(@PathVariable UUID originZoneId, @PathVariable UUID destinationZoneId,
                                     @Valid @RequestBody PricingUpdateRequest body) {
        return service.upsert(originZoneId, destinationZoneId, body);
    }

    @GetMapping("/size-surcharges")
    @Operation(summary = "List size surcharges", description = "FR-PRC-007")
    public List<SizeSurchargeResponse> surcharges() {
        return service.surcharges();
    }

    @PutMapping("/size-surcharges")
    @Operation(summary = "Replace the three size surcharges", description = "FR-PRC-007: body is a list of "
            + "{size, surcharge} with SMALL, MEDIUM and LARGE once each. Audited (SURCHARGES_UPDATED).")
    public List<SizeSurchargeResponse> updateSurcharges(
            @RequestBody @NotNull @Size(min = 3, max = 3) List<@Valid SizeSurchargeUpdateItem> body) {
        return service.updateSurcharges(body);
    }
}
