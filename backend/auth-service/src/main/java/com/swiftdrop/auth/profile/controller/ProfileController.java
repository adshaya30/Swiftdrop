package com.swiftdrop.auth.profile.controller;

import com.swiftdrop.auth.profile.dto.ChangePasswordRequest;
import com.swiftdrop.auth.profile.dto.UpdateProfileRequest;
import com.swiftdrop.auth.profile.dto.UserProfileResponse;
import com.swiftdrop.auth.profile.service.ProfileService;
import com.swiftdrop.auth.security.AuthenticatedUser;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.util.PhoneNumberNormalizer;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * Endpoints for user profile management and password change.
 * SRS v2.1 – FR-AUTH-012, FR-AUTH-013, FR-AUTH-014
 */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private static final String REFRESH_COOKIE_NAME = "sd_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final ProfileService profileService;
    private final boolean cookieSecure;

    public ProfileController(
            ProfileService profileService,
            @Value("${auth.cookie.secure:${COOKIE_SECURE:false}}") boolean cookieSecure) {
        this.profileService = profileService;
        this.cookieSecure = cookieSecure;
    }

    /**
     * GET /api/v1/me
     * Returns current authenticated user's profile.
     */
    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        UserProfileResponse response = profileService.getProfile(user.userId());
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/me
     * Updates editable profile fields (fullName, phone).
     */
    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (request.phone() != null && !request.phone().isBlank()) {
            if (PhoneNumberNormalizer.normalize(request.phone()).isEmpty()) {
                throw new DomainException(
                        ErrorCode.VALIDATION_ERROR,
                        "Request validation failed.",
                        List.of(new FieldError("phone", "Must be a valid Sri Lankan phone number")));
            }
        }
        UserProfileResponse response = profileService.updateProfile(user.userId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/me/change-password
     * Changes password, revokes all refresh tokens, clears refresh cookie, and returns 204 No Content.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(user.userId(), request);

        ResponseCookie clearCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }
}

