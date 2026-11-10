package com.swiftdrop.auth.merchant.controller;

import com.swiftdrop.auth.merchant.dto.InternalMerchantView;
import com.swiftdrop.auth.merchant.dto.InternalUserContact;
import com.swiftdrop.auth.merchant.service.InternalLookupService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoints for other services (SRS 7.4, FR-AUTH-022). Secured only by X-Internal-Key (the shared
 * common-lib InternalKeyFilter, registered in InternalEndpointsConfig) and never routed through the gateway.
 */
@RestController
@RequestMapping("/internal/v1")
@Hidden
public class InternalLookupController {

    private final InternalLookupService service;

    public InternalLookupController(InternalLookupService service) {
        this.service = service;
    }

    @GetMapping("/users/{id}/contact")
    public InternalUserContact contact(@PathVariable("id") String id) {
        return service.contact(id);
    }

    @GetMapping("/merchants/{merchantId}")
    public InternalMerchantView merchant(@PathVariable("merchantId") String merchantId) {
        return service.merchant(merchantId);
    }
}
