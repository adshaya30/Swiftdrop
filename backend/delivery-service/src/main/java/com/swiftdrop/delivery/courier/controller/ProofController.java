package com.swiftdrop.delivery.courier.controller;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.delivery.courier.dto.DeliveryProofDto;
import com.swiftdrop.delivery.courier.service.CourierDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/proofs")
@PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT', 'COURIER', 'ADMIN')")
@Tag(name = "Delivery Proofs", description = "Endpoints for delivery proof and photo retrieval")
public class ProofController {

    private final CourierDeliveryService courierDeliveryService;

    public ProofController(CourierDeliveryService courierDeliveryService) {
        this.courierDeliveryService = courierDeliveryService;
    }

    @GetMapping("/{parcelId}")
    @Operation(summary = "Get delivery proof metadata for authorized viewer")
    public DeliveryProofDto getProof(@PathVariable("parcelId") UUID parcelId) {
        CurrentUser caller = CurrentUsers.get();
        return courierDeliveryService.getProof(caller, parcelId);
    }

    @GetMapping("/{parcelId}/photo")
    @Operation(summary = "Get delivery proof photo binary (JPEG)")
    public ResponseEntity<byte[]> getProofPhoto(@PathVariable("parcelId") UUID parcelId) {
        CurrentUser caller = CurrentUsers.get();
        byte[] photoBytes = courierDeliveryService.getProofPhoto(caller, parcelId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .contentType(MediaType.IMAGE_JPEG)
                .body(photoBytes);
    }
}

