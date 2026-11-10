package com.swiftdrop.auth.merchant.controller;

import com.swiftdrop.auth.merchant.dto.RegisterMerchantRequest;
import com.swiftdrop.auth.merchant.dto.RegisterMerchantResponse;
import com.swiftdrop.auth.merchant.service.MerchantRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public merchant registration (FR-AUTH-016). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Merchant onboarding")
public class MerchantRegistrationController {

    private final MerchantRegistrationService registrationService;

    public MerchantRegistrationController(MerchantRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register-merchant")
    @Operation(summary = "Register a merchant account (waits for admin approval)")
    public ResponseEntity<RegisterMerchantResponse> register(@Valid @RequestBody RegisterMerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }
}
