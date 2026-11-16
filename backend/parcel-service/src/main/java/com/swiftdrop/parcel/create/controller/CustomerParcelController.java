package com.swiftdrop.parcel.create.controller;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.parcel.create.dto.*;
import com.swiftdrop.parcel.create.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import com.swiftdrop.parcel.lifecycle.service.ParcelTransitionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** FR-PAR-005/009: role checks precede owner-filtered service calls. */
@SecurityScheme(name = "customerBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityRequirement(name = "customerBearer")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: invalid request", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or TOKEN_EXPIRED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED (CUSTOMER user token required) or PASSWORD_CHANGE_REQUIRED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
})
@RestController
@RequestMapping("/api/v1/customer/parcels")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerParcelController {
    private final ParcelCreationService creation;
    private final IdempotencyService idempotency;
    private final CustomerParcelQueryService queries;
    private final ParcelTransitionService transitions;

    public CustomerParcelController(ParcelCreationService creation, IdempotencyService idempotency,
                                     CustomerParcelQueryService queries, ParcelTransitionService transitions) {
        this.creation = creation;
        this.idempotency = idempotency;
        this.queries = queries;
        this.transitions = transitions;
    }

    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created or replayed within 24 hours", headers = {
            @Header(name = "Location", description = "Customer parcel detail URL", schema = @Schema(type = "string")),
            @Header(name = "Idempotent-Replay", description = "true only for an idempotent replay", schema = @Schema(type = "boolean"))},
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CustomerParcelCreatedResponse.class))),
        @ApiResponse(responseCode = "409", description = "FEE_CHANGED: fee extension contains the current quote", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "422", description = "IDEMPOTENCY_KEY_REUSED, ZONE_NOT_SERVICEABLE or PRICING_NOT_CONFIGURED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: pricing unavailable", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
    })
    @PostMapping
    @Operation(summary = "Create a customer parcel", description = "FR-PAR-005/006: optional idempotency key; confirms authoritative pricing.")
    public ResponseEntity<Object> create(@Valid @RequestBody CustomerParcelCreationRequest body,
            @Parameter(description = "Optional key scoped to this customer; valid for 24 hours", schema = @Schema(minLength = 1, maxLength = 64, pattern = "[A-Za-z0-9_-]+"))
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        var result = creation.create(new CreationRequest(OwnerType.CUSTOMER, CurrentUsers.get().userId(),
                body.toParcelInput(), body.expectedFee(), null, key, idempotency.hashRequest(body),
                () -> BigDecimal.ZERO, CustomerParcelCreatedResponse::from));
        var response = ResponseEntity.status(result.httpStatus())
                .location(URI.create("/api/v1/customer/parcels/" + result.parcelId()));
        if (result.replay()) response.header("Idempotent-Replay", "true");
        return response.body(result.body());
    }

    @ApiResponse(responseCode = "200", description = "Paginated owned parcels", useReturnTypeSchema = true, content = @Content(mediaType = "application/json"))
    @GetMapping
    @Operation(summary = "List the customer's parcels", description = "FR-PAR-009: dates are inclusive Asia/Colombo dates; q is a tracking-number prefix. Page is zero-based; size is 1-100. Sort: createdAt, status or trackingNumber followed by asc or desc.")
    public Pagination<ParcelDetailDto> list(@RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) @Size(max = 13) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.list(CurrentUsers.get().userId(), status, from, to, q, page, size, sort);
    }

    @ApiResponse(responseCode = "404", description = "NOT_FOUND: unknown or foreign parcel", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
    @ApiResponse(responseCode = "200", description = "Owned parcel detail", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ParcelDetailDto.class)))
    @GetMapping("/{id}")
    @Operation(summary = "Get an owned parcel", description = "FR-PAR-009: unknown and foreign parcels return 404.")
    public ParcelDetailDto detail(@PathVariable UUID id) {
        return queries.detail(CurrentUsers.get().userId(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an owned CREATED parcel", description = "FR-PAR-009: optional reason, at most 300 characters. Lifecycle service checks accessibility and the transition atomically.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cancelled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CustomerParcelCancellationResponse.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND: unknown or foreign parcel", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION or CONCURRENT_UPDATE", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
    })
    public CustomerParcelCancellationResponse cancel(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CustomerParcelCancellationRequest body) {
        var result = transitions.cancelAsOwner(id, CurrentUsers.get(), body == null ? null : body.reason());
        return new CustomerParcelCancellationResponse(result.parcelId(), result.trackingNumber(),
                result.status(), result.statusChangedAt());
    }
}
