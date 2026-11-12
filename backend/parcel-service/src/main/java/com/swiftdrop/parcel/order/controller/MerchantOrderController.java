package com.swiftdrop.parcel.order.controller;

import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.parcel.order.dto.MerchantOrderCreatedResponse;
import com.swiftdrop.parcel.order.dto.MerchantOrderCreationRequest;
import com.swiftdrop.parcel.order.security.OrderAccess;
import com.swiftdrop.parcel.order.service.MerchantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-PAR-007/008: merchant order API. Client tokens are accepted here only (SRS 3.6). */
@SecurityScheme(name = "merchantBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Merchant dashboard token, or client-credentials token with orders:read / orders:write")
@SecurityRequirement(name = "merchantBearer")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or TOKEN_EXPIRED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED (role or scope) or MERCHANT_NOT_ACTIVE", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
})
@RestController
@RequestMapping("/api/v1/merchant/orders")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantOrderController {

    private static final String PATH = "/api/v1/merchant/orders/";

    private final MerchantOrderService orders;
    private final OrderAccess access;

    public MerchantOrderController(MerchantOrderService orders, OrderAccess access) {
        this.orders = orders;
        this.access = access;
    }

    @PostMapping
    @Operation(summary = "Create an order",
            description = "FR-PAR-007: scope orders:write. Optional Idempotency-Key (24 h, scoped to the merchant). "
                    + "expectedFee is optional and compared only when present.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created, or replayed within 24 hours", headers = {
            @Header(name = "Location", description = "Order detail URL", schema = @Schema(type = "string")),
            @Header(name = "Idempotent-Replay", description = "true only for an idempotent replay", schema = @Schema(type = "boolean"))},
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MerchantOrderCreatedResponse.class))),
        @ApiResponse(responseCode = "409", description = "FEE_CHANGED or DUPLICATE_MERCHANT_REFERENCE", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "422", description = "IDEMPOTENCY_KEY_REUSED, ZONE_NOT_SERVICEABLE or PRICING_NOT_CONFIGURED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: auth or pricing unavailable", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
    })
    public ResponseEntity<Object> create(@Valid @RequestBody MerchantOrderCreationRequest body,
            @Parameter(description = "Optional key scoped to this merchant; valid for 24 hours",
                    schema = @Schema(minLength = 1, maxLength = 64, pattern = "[A-Za-z0-9_-]+"))
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        UUID merchantId = access.requireWrite();
        var result = orders.create(merchantId, body, key);
        var response = ResponseEntity.status(result.httpStatus()).location(URI.create(PATH + result.parcelId()));
        if (result.replay()) {
            response.header("Idempotent-Replay", "true");
        }
        return response.body(result.body());
    }
}
