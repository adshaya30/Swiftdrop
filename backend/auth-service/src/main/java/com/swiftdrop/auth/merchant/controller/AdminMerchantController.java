package com.swiftdrop.auth.merchant.controller;

import com.swiftdrop.auth.merchant.dto.AdminMerchantDetail;
import com.swiftdrop.auth.merchant.dto.AdminMerchantSummary;
import com.swiftdrop.auth.merchant.dto.DiscountRequest;
import com.swiftdrop.auth.merchant.dto.StatusReasonRequest;
import com.swiftdrop.auth.merchant.service.MerchantAdminService;
import com.swiftdrop.auth.security.AuthenticatedUser;
import com.swiftdrop.common.paging.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Merchant administration for ADMIN users (FR-AUTH-017). The role check runs before anything else (BR-25). */
@RestController
@RequestMapping("/api/v1/admin/merchants")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin merchants")
public class AdminMerchantController {

    private final MerchantAdminService service;

    public AdminMerchantController(MerchantAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List merchants (filters: status, q = business name prefix)")
    public Pagination<AdminMerchantSummary> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", required = false) String sort) {
        return service.list(status, q, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Merchant details")
    public AdminMerchantDetail get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve (from PENDING or REJECTED)")
    public ResponseEntity<AdminMerchantDetail> approve(@PathVariable("id") UUID id,
                                                       @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(service.approve(id, admin.userId()));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject (from PENDING, reason 10-300 characters)")
    public ResponseEntity<AdminMerchantDetail> reject(@PathVariable("id") UUID id,
                                                      @AuthenticationPrincipal AuthenticatedUser admin,
                                                      @Valid @RequestBody StatusReasonRequest request) {
        return ResponseEntity.ok(service.reject(id, admin.userId(), request.reason()));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend (from APPROVED, reason 10-300 characters)")
    public ResponseEntity<AdminMerchantDetail> suspend(@PathVariable("id") UUID id,
                                                       @AuthenticationPrincipal AuthenticatedUser admin,
                                                       @Valid @RequestBody StatusReasonRequest request) {
        return ResponseEntity.ok(service.suspend(id, admin.userId(), request.reason()));
    }

    @PostMapping("/{id}/reinstate")
    @Operation(summary = "Reinstate (from SUSPENDED)")
    public ResponseEntity<AdminMerchantDetail> reinstate(@PathVariable("id") UUID id,
                                                         @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(service.reinstate(id, admin.userId()));
    }

    @PatchMapping("/{id}/discount")
    @Operation(summary = "Set the discount percent (0.00 to 50.00)")
    public ResponseEntity<AdminMerchantDetail> discount(@PathVariable("id") UUID id,
                                                        @AuthenticationPrincipal AuthenticatedUser admin,
                                                        @Valid @RequestBody DiscountRequest request) {
        return ResponseEntity.ok(service.setDiscount(id, admin.userId(), request.discountPercent()));
    }
}
