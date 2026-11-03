package com.swiftdrop.auth.controller;

import com.swiftdrop.auth.dto.request.RegistrationRequest;
import com.swiftdrop.auth.dto.response.UserResponse;
import com.swiftdrop.auth.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints.
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-002
 *
 * <p>Only Feature 1 (registration) is implemented here.
 * Login (Feature 2) will be added in a subsequent branch.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * POST /api/v1/auth/register
     *
     * <p>Registers a new customer account.
     * Does NOT issue JWT or refresh tokens (that is Feature 2: Login).</p>
     *
     * @param request validated registration payload
     * @return 201 Created with safe user summary
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegistrationRequest request) {
        UserResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
