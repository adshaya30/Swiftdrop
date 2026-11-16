package com.swiftdrop.delivery.courier.controller;

import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.delivery.courier.dto.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.courier.dto.ParcelStatusUpdateResponse;
import com.swiftdrop.delivery.courier.service.CourierDeliveryService;
import com.swiftdrop.delivery.domain.DeliveryListItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/courier/deliveries")
@PreAuthorize("hasRole('COURIER')")
@Tag(name = "Courier Operations", description = "Endpoints for assigned courier deliveries and status updates")
public class CourierDeliveryController {

    private final CourierDeliveryService courierDeliveryService;

    public CourierDeliveryController(CourierDeliveryService courierDeliveryService) {
        this.courierDeliveryService = courierDeliveryService;
    }

    @GetMapping
    @Operation(summary = "List deliveries assigned to the calling courier by scope")
    public Pagination<DeliveryListItem> listDeliveries(
            @RequestParam(value = "scope", defaultValue = "today") String scope,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        CurrentUser user = CurrentUsers.get();
        return courierDeliveryService.listDeliveries(user.userId(), scope, page, size);
    }

    @GetMapping("/{parcelId}")
    @Operation(summary = "Get delivery detail for assigned courier without fees")
    public CourierDeliveryDetailResponse getDeliveryDetail(@PathVariable("parcelId") UUID parcelId) {
        CurrentUser user = CurrentUsers.get();
        return courierDeliveryService.getDeliveryDetail(user.userId(), parcelId);
    }

    @PostMapping(value = "/{parcelId}/status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update delivery status as courier (Flow 3)")
    public ParcelStatusUpdateResponse updateStatus(
            @PathVariable("parcelId") UUID parcelId,
            @RequestParam("status") ParcelStatus status,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "recipientName", required = false) String recipientName,
            @RequestParam(value = "failureReason", required = false) FailureReason failureReason,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        CurrentUser user = CurrentUsers.get();
        return courierDeliveryService.updateDeliveryStatus(
                user.userId(),
                parcelId,
                status,
                note,
                recipientName,
                failureReason,
                photo
        );
    }
}

