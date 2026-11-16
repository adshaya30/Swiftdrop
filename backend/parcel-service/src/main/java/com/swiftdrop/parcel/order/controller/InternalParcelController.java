package com.swiftdrop.parcel.order.controller;

import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.parcel.create.dto.InternalParcelDetailDto;
import com.swiftdrop.parcel.order.dto.InternalTransitionRequest;
import com.swiftdrop.parcel.order.dto.InternalTransitionResponse;
import com.swiftdrop.parcel.order.dto.RecipientContact;
import com.swiftdrop.parcel.order.service.InternalParcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-PAR-016, SRS 7.4: secured only by X-Internal-Key (common-lib's internal chain, constant-time compare, NFR-SEC-017)
 * and never routed through the gateway.
 */
@RestController
@RequestMapping("/internal/v1/parcels")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Missing or wrong X-Internal-Key", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
    @ApiResponse(responseCode = "404", description = "NOT_FOUND: unknown parcel", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
})
public class InternalParcelController {

    private final InternalParcelService parcels;

    public InternalParcelController(InternalParcelService parcels) {
        this.parcels = parcels;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full parcel detail", description = "Admin detail plus statusSequence (Flow 3).")
    public InternalParcelDetailDto detail(@PathVariable UUID id) {
        return parcels.detail(id);
    }

    @GetMapping("/{id}/recipient")
    @Operation(summary = "Drop-off contact", description = "FR-NTF-007: {name, phone, email} for notifications.")
    public RecipientContact recipient(@PathVariable UUID id) {
        return parcels.recipient(id);
    }

    @PostMapping("/{id}/transition")
    @Operation(summary = "Perform a status transition",
            description = "actorRole COURIER or ADMIN; validated by ParcelTransitionService (Table 2.3, 2.4).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transition applied", content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalTransitionResponse.class))),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION or CONCURRENT_UPDATE", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
    })
    public InternalTransitionResponse transition(@PathVariable UUID id,
                                                 @Valid @RequestBody InternalTransitionRequest request) {
        return parcels.transition(id, request);
    }
}
