package com.swiftdrop.parcel.order.controller;

import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.parcel.order.dto.MerchantDashboardSummary;
import com.swiftdrop.parcel.order.security.OrderAccess;
import com.swiftdrop.parcel.order.service.MerchantDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-PAR-014: GET /api/v1/merchant/dashboard/summary, merchant dashboard login only. */
@SecurityRequirement(name = "merchantBearer")
@RestController
public class MerchantDashboardController {

    private final MerchantDashboardService dashboard;
    private final OrderAccess access;

    public MerchantDashboardController(MerchantDashboardService dashboard, OrderAccess access) {
        this.dashboard = dashboard;
        this.access = access;
    }

    @GetMapping("/api/v1/merchant/dashboard/summary")
    @PreAuthorize("hasRole('MERCHANT')")
    @Operation(summary = "Merchant dashboard summary",
            description = "FR-PAR-014: order counts per active status, per status for the last 30 days, and the "
                    + "10 most recent orders. Dashboard login only; client tokens get 403.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MerchantDashboardSummary.class))),
        @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: not a merchant, or a client token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
    })
    public MerchantDashboardSummary summary() {
        return dashboard.summary(access.requireDashboardUser());
    }
}
