package com.swiftdrop.pricing.core.controller;

import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.pricing.core.dto.request.QuoteRequest;
import com.swiftdrop.pricing.core.dto.response.DistrictsResponse;
import com.swiftdrop.pricing.core.dto.response.QuoteResponse;
import com.swiftdrop.pricing.core.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
public class PublicPricingController {
    private final PricingService service;

    public PublicPricingController(PricingService service) { this.service = service; }

    @PostMapping("/quotes")
    @Operation(summary = "Quote a parcel with no merchant discount", description = "FR-PRC-002; BR-08")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "LKR quote", content = @Content(schema = @Schema(implementation = QuoteResponse.class))),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR", content = @Content(schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "422", description = "ZONE_NOT_SERVICEABLE or PRICING_NOT_CONFIGURED", content = @Content(schema = @Schema(implementation = ProblemBody.class)))
    })
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) { return service.quote(request); }

    @GetMapping("/districts")
    @Operation(summary = "List active districts, parcel sizes and the configured maximum weight", description = "FR-PRC-005")
    @ApiResponse(responseCode = "200", description = "Available reference data", content = @Content(schema = @Schema(implementation = DistrictsResponse.class)))
    public DistrictsResponse districts() { return service.districts(); }
}
