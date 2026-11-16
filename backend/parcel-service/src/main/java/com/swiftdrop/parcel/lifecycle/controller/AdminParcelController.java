package com.swiftdrop.parcel.lifecycle.controller;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.parcel.create.dto.AdminParcelDetailDto;
import com.swiftdrop.parcel.lifecycle.dto.AdminCancelRequest;
import com.swiftdrop.parcel.lifecycle.dto.AdminFailRequest;
import com.swiftdrop.parcel.lifecycle.dto.AdminParcelActionResponse;
import com.swiftdrop.parcel.lifecycle.service.AdminParcelQueryService;
import com.swiftdrop.parcel.lifecycle.service.ParcelTransitionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-PAR-011 and FR-PAR-012: admin parcel search, detail, cancel and force-fail. ADMIN only; every other role gets
 * 403 ACCESS_DENIED and client-credentials tokens are refused by common-lib (SRS 3.6). The controller holds no
 * business logic: status changes go through {@link ParcelTransitionService}.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/parcels")
@PreAuthorize("hasRole('ADMIN')")
public class AdminParcelController {

    private final AdminParcelQueryService queries;
    private final ParcelTransitionService transitions;

    public AdminParcelController(AdminParcelQueryService queries, ParcelTransitionService transitions) {
        this.queries = queries;
        this.transitions = transitions;
    }

    @GetMapping
    @Operation(summary = "Search all parcels",
            description = "FR-PAR-011: filters status, ownerType, from, to (inclusive Asia/Colombo dates), "
                    + "q (tracking number or merchant reference prefix), originDistrictCode and "
                    + "destinationDistrictCode. Page is zero-based, size 1-100. Sort: createdAt, updatedAt, "
                    + "status or trackingNumber followed by asc or desc.")
    public Pagination<AdminParcelDetailDto> list(@RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) OwnerType ownerType,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) @Size(max = 64) String q,
            @RequestParam(required = false) @Size(max = 20) String originDistrictCode,
            @RequestParam(required = false) @Size(max = 20) String destinationDistrictCode,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.list(status, ownerType, from, to, q, originDistrictCode, destinationDistrictCode, page,
                size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a parcel", description = "FR-PAR-011: full detail incl. contacts, addresses, fee, "
            + "owner ids and merchant reference.")
    public AdminParcelDetailDto detail(@PathVariable UUID id) {
        return queries.detail(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a CREATED parcel", description = "FR-PAR-011, BR-07: reason 10-300 characters. "
            + "Audited (PARCEL_CANCELLED_BY_ADMIN).")
    public AdminParcelActionResponse cancel(@PathVariable UUID id, @Valid @RequestBody AdminCancelRequest body) {
        return AdminParcelActionResponse.from(transitions.cancelAsAdmin(id, CurrentUsers.get(), body.reason()));
    }

    @PostMapping("/{id}/fail")
    @Operation(summary = "Force-fail a parcel stuck after pickup", description = "FR-PAR-012: only from PICKED_UP, "
            + "IN_TRANSIT or OUT_FOR_DELIVERY; reason PARCEL_DAMAGED or OTHER; note 5-500 characters. "
            + "Audited (PARCEL_FAILED_BY_ADMIN).")
    public AdminParcelActionResponse fail(@PathVariable UUID id, @Valid @RequestBody AdminFailRequest body) {
        return AdminParcelActionResponse.from(
                transitions.failAsAdmin(id, CurrentUsers.get(), body.failureReason(), body.note()));
    }
}
