package com.swiftdrop.pricing.core.controller;

import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.pricing.core.dto.request.CalculatePricingRequest;
import com.swiftdrop.pricing.core.dto.response.InternalPricingResponse;
import com.swiftdrop.pricing.core.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/pricing")
@SecurityScheme(name = "internalKey", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-Internal-Key")
public class InternalPricingController {
    private final PricingService service;

    public InternalPricingController(PricingService service) { this.service = service; }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate a discounted parcel fee and creation snapshots", description = "FR-PRC-003; SRS 7.4")
    @SecurityRequirement(name = "internalKey")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Calculated fee and snapshots", content = @Content(schema = @Schema(implementation = InternalPricingResponse.class))),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR", content = @Content(schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid internal key", content = @Content(schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "422", description = "ZONE_NOT_SERVICEABLE or PRICING_NOT_CONFIGURED", content = @Content(schema = @Schema(implementation = ProblemBody.class)))
    })
    public InternalPricingResponse calculate(@Valid @RequestBody CalculatePricingRequest request) {
        return service.calculate(request);
    }
}
