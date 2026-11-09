package com.swiftdrop.auth.merchant.controller;

import com.swiftdrop.auth.merchant.dto.PendingMerchantsResponse;
import com.swiftdrop.auth.merchant.security.RoleGuard;
import com.swiftdrop.auth.merchant.service.MerchantAdminService;
import com.swiftdrop.auth.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin dashboard counter for the pending-merchants card (FR-AUTH-024). */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin merchants")
public class AdminMerchantDashboardController {

    private final MerchantAdminService service;

    public AdminMerchantDashboardController(MerchantAdminService service) {
        this.service = service;
    }

    @GetMapping("/merchants")
    @Operation(summary = "Number of merchants waiting for approval")
    public PendingMerchantsResponse pendingMerchants(@AuthenticationPrincipal AuthenticatedUser admin) {
        RoleGuard.require(admin, "ADMIN");
        return new PendingMerchantsResponse(service.pendingCount());
    }
}
