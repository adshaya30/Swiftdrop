package com.swiftdrop.delivery.assignment.controller;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.delivery.assignment.dto.AdminDeliveryListItem;
import com.swiftdrop.delivery.assignment.dto.AssignDeliveryRequest;
import com.swiftdrop.delivery.assignment.dto.CourierWorkloadResponse;
import com.swiftdrop.delivery.assignment.dto.DeliveryDetailResponse;
import com.swiftdrop.delivery.assignment.dto.DispatchDashboardResponse;
import com.swiftdrop.delivery.assignment.service.DeliveryAdminService;
import com.swiftdrop.delivery.domain.DeliveryListItem;
import com.swiftdrop.delivery.domain.Scope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryController {

    private final DeliveryAdminService service;

    public AdminDeliveryController(DeliveryAdminService service) {
        this.service = service;
    }

    @PostMapping("/deliveries/{parcelId}/assign")
    public DeliveryDetailResponse assign(@PathVariable UUID parcelId, @Valid @RequestBody AssignDeliveryRequest request) {
        return service.assign(parcelId, request.courierId());
    }

    @GetMapping("/deliveries")
    public Pagination<AdminDeliveryListItem> listDeliveries(
            @RequestParam(required = false) Boolean assigned,
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) UUID courierId,
            @RequestParam(required = false) String originDistrictCode,
            @RequestParam(required = false) String destinationDistrictCode,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort) {
        return service.listDeliveries(assigned, status, courierId, originDistrictCode, destinationDistrictCode,
                from, to, q, page, size, sort);
    }

    @GetMapping("/deliveries/{parcelId}")
    public DeliveryDetailResponse getDelivery(@PathVariable UUID parcelId) {
        return service.getDelivery(parcelId);
    }

    @GetMapping("/couriers")
    public List<CourierWorkloadResponse> listCouriers() {
        return service.listCouriers();
    }

    @GetMapping("/couriers/{courierId}/deliveries")
    public Pagination<DeliveryListItem> listCourierDeliveries(
            @PathVariable UUID courierId,
            @RequestParam(defaultValue = "today") String scope,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Scope parsedScope;
        try {
            parsedScope = Scope.valueOf(scope.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new com.swiftdrop.common.error.DomainException(
                    com.swiftdrop.common.error.ErrorCode.VALIDATION_ERROR, "Invalid delivery scope");
        }
        return service.listCourierDeliveries(courierId, parsedScope, page, size);
    }

    @GetMapping("/dashboard/dispatch")
    public DispatchDashboardResponse dispatchDashboard() {
        return service.dispatchDashboard();
    }
}