package com.swiftdrop.auth.merchant.controller;

import com.swiftdrop.auth.merchant.dto.CreateCredentialRequest;
import com.swiftdrop.auth.merchant.dto.CredentialCreatedResponse;
import com.swiftdrop.auth.merchant.dto.CredentialSummary;
import com.swiftdrop.auth.merchant.security.RoleGuard;
import com.swiftdrop.auth.merchant.service.MerchantCredentialService;
import com.swiftdrop.auth.security.AuthenticatedUser;
import com.swiftdrop.common.paging.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API credentials for the logged-in MERCHANT (dashboard login, typ=user; typ=client tokens are refused by the shared
 * token-type rule). FR-AUTH-018. The role is checked explicitly by {@link RoleGuard} as well as by
 * {@code @PreAuthorize}, which only takes effect once auth-service uses common-lib's security configuration.
 */
@RestController
@RequestMapping("/api/v1/merchant/credentials")
@PreAuthorize("hasRole('MERCHANT')")
@Tag(name = "Merchant API credentials")
public class MerchantCredentialController {

    private static final String MERCHANT = "MERCHANT";

    private final MerchantCredentialService service;

    public MerchantCredentialController(MerchantCredentialService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a credential; the client secret is shown once")
    public ResponseEntity<CredentialCreatedResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @Valid @RequestBody CreateCredentialRequest request) {
        RoleGuard.require(user, MERCHANT);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(service.create(user.userId(), request.label()));
    }

    @GetMapping
    @Operation(summary = "List credentials (never the secret)")
    public Pagination<CredentialSummary> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(name = "page", defaultValue = "0") int page,
                                              @RequestParam(name = "size", defaultValue = "20") int size) {
        RoleGuard.require(user, MERCHANT);
        return service.list(user.userId(), page, size);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke a credential")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable("id") UUID id) {
        RoleGuard.require(user, MERCHANT);
        service.revoke(user.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
