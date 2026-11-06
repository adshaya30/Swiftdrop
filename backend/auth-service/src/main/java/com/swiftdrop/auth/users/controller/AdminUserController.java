package com.swiftdrop.auth.users.controller;

import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.security.AuthenticatedUser;
import com.swiftdrop.auth.users.dto.*;
import com.swiftdrop.auth.users.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Administrative endpoints for user management.
 * Protected by ADMIN role only.
 * SRS v2.1 – FR-AUTH-020, FR-AUTH-021
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * GET /api/v1/admin/users
     * Lists users with role, status, and text search filters.
     */
    @GetMapping
    public ResponseEntity<Page<AdminUserSummaryResponse>> listUsers(
            @RequestParam(name = "role", required = false) UserRole role,
            @RequestParam(name = "status", required = false) UserStatus status,
            @RequestParam(name = "q", required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AdminUserSummaryResponse> users = adminUserService.listUsers(role, status, query, pageable);
        return ResponseEntity.ok(users);
    }

    /**
     * GET /api/v1/admin/users/{id}
     * Retrieves detailed user summary.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserSummaryResponse> getUser(@PathVariable("id") UUID id) {
        AdminUserSummaryResponse user = adminUserService.getUser(id);
        return ResponseEntity.ok(user);
    }

    /**
     * POST /api/v1/admin/users
     * Creates an ADMIN or COURIER user with a 16-character temporary password returned once.
     */
    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateUserRequest request) {

        CreateUserResponse response = adminUserService.createUser(actor.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PATCH /api/v1/admin/users/{id}
     * Modifies permitted user details (fullName, phone, vehicle info).
     */
    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserSummaryResponse> updateUser(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("id") UUID id,
            @Valid @RequestBody EditUserRequest request) {

        AdminUserSummaryResponse response = adminUserService.updateUser(actor.userId(), id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/users/{id}/activate
     * Activates a user account.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<AdminUserSummaryResponse> activateUser(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("id") UUID id) {

        AdminUserSummaryResponse response = adminUserService.activateUser(actor.userId(), id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/users/{id}/deactivate
     * Deactivates a user account with courier delivery guard and token revocation.
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<AdminUserSummaryResponse> deactivateUser(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("id") UUID id) {

        AdminUserSummaryResponse response = adminUserService.deactivateUser(actor.userId(), id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/users/{id}/reset-password
     * Generates a new 16-character temporary password returned once.
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("id") UUID id) {

        ResetPasswordResponse response = adminUserService.resetPassword(actor.userId(), id);
        return ResponseEntity.ok(response);
    }
}
