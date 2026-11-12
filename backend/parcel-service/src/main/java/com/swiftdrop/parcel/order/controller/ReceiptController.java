package com.swiftdrop.parcel.order.controller;

import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.parcel.order.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-PAR-010: GET /api/v1/parcels/{id}/receipt. Roles CUSTOMER, MERCHANT and ADMIN only: the role check runs first,
 * so a COURIER gets 403 before ownership is looked at (FR-PAR-017); client tokens are refused by common-lib.
 */
@SecurityScheme(name = "receiptBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityRequirement(name = "receiptBearer")
@RestController
public class ReceiptController {

    private final ReceiptService receipts;

    public ReceiptController(ReceiptService receipts) {
        this.receipts = receipts;
    }

    @GetMapping(value = "/api/v1/parcels/{id}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Download the delivery receipt",
            description = "FR-PAR-010: PDF generated on demand. Owner customer, owner merchant (dashboard login) or admin.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "SwiftDrop-Receipt-<trackingNumber>.pdf", content = @Content(mediaType = "application/pdf")),
        @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or TOKEN_EXPIRED", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: courier, client token, or another role", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND: unknown parcel or not the owner", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemBody.class)))
    })
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        ReceiptService.Receipt receipt = receipts.generate(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("SwiftDrop-Receipt-" + receipt.trackingNumber() + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(receipt.pdf());
    }
}
