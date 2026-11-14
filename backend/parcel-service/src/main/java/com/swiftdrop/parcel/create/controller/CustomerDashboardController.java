package com.swiftdrop.parcel.create.controller;

import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.parcel.create.dto.CustomerDashboardSummary;
import com.swiftdrop.parcel.create.service.CustomerParcelQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-PAR-015: Customer dashboard summary.
 */
@SecurityRequirement(name = "customerBearer")
@RestController
@RequestMapping("/api/v1/customer/dashboard")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerDashboardController {

    private final CustomerParcelQueryService queries;

    public CustomerDashboardController(CustomerParcelQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/summary")
    @Operation(summary = "Customer dashboard summary",
            description = "FR-PAR-015: returns counts of active and delivered parcels and the five most recent parcels.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = CustomerDashboardSummary.class))),
        @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or TOKEN_EXPIRED",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: customer login only",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
    })
    public CustomerDashboardSummary summary() {
        return queries.dashboardSummary(CurrentUsers.get().userId());
    }
}

