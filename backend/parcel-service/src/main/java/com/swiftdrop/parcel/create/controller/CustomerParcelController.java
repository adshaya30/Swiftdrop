package com.swiftdrop.parcel.create.controller;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.parcel.create.dto.*;
import com.swiftdrop.parcel.create.service.*;
import io.swagger.v3.oas.annotations.Operation;
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
@RestController
@RequestMapping("/api/v1/customer/parcels")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerParcelController {
    private final ParcelCreationService creation;
    private final IdempotencyService idempotency;
    private final CustomerParcelQueryService queries;

    public CustomerParcelController(ParcelCreationService creation, IdempotencyService idempotency,
                                     CustomerParcelQueryService queries) {
        this.creation = creation;
        this.idempotency = idempotency;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Create a customer parcel", description = "FR-PAR-005/006: optional idempotency key; confirms authoritative pricing.")
    public ResponseEntity<Object> create(@Valid @RequestBody CustomerParcelCreationRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        var result = creation.create(new CreationRequest(OwnerType.CUSTOMER, CurrentUsers.get().userId(),
                body.toParcelInput(), body.expectedFee(), null, key, idempotency.hashRequest(body),
                () -> BigDecimal.ZERO, CustomerParcelCreatedResponse::from));
        var response = ResponseEntity.status(result.httpStatus())
                .location(URI.create("/api/v1/customer/parcels/" + result.parcelId()));
        if (result.replay()) response.header("Idempotent-Replay", "true");
        return response.body(result.body());
    }

    @GetMapping
    @Operation(summary = "List the customer's parcels", description = "FR-PAR-009: dates are inclusive Asia/Colombo dates.")
    public Pagination<ParcelDetailDto> list(@RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) @Size(max = 13) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.list(CurrentUsers.get().userId(), status, from, to, q, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an owned parcel", description = "FR-PAR-009: unknown and foreign parcels return 404.")
    public ParcelDetailDto detail(@PathVariable UUID id) {
        return queries.detail(CurrentUsers.get().userId(), id);
    }
}
