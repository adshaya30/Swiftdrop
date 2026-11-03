package com.swiftdrop.auth.controller;

import com.swiftdrop.auth.dto.admin.*;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.security.AuthenticatedUser;
import com.swiftdrop.auth.service.AdminUserService;
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
 * Controller providing administrative user management operations.
 * Requires authenticated user with role ADMIN.
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
     * Lists users with optional filters and pagination.
     */
    @GetMapping
    public ResponseEntity<Page<AdminUserSummaryResponse>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AdminUserSummaryResponse> users = adminUserService.listUsers(role, status, q, pageable);
        return ResponseEntity.ok(users);
    }

    /**
     * POST /api/v1/admin/users
     * Creates a new ADMIN or COURIER user with a 16-character temporary password.
     */
    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @Valid @RequestBody CreateUserRequest request) {
        CreateUserResponse response = adminUserService.createUser(admin != null ? admin.userId() : null, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/admin/users/{id}
     * Retrieves detailed user summary.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserSummaryResponse> getUser(@PathVariable UUID id) {
        AdminUserSummaryResponse response = adminUserService.getUser(id);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/v1/admin/users/{id}
     * Updates permitted user and vehicle details.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserSummaryResponse> editUser(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable UUID id,
            @Valid @RequestBody EditUserRequest request) {
        AdminUserSummaryResponse response = adminUserService.editUser(admin != null ? admin.userId() : null, id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/users/{id}/activate
     * Activates a user account.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<AdminUserSummaryResponse> activateUser(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable UUID id) {
        AdminUserSummaryResponse response = adminUserService.activateUser(admin != null ? admin.userId() : null, id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/users/{id}/deactivate
     * Deactivates a user account (enforcing self-deactivation guard and courier active delivery check).
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<AdminUserSummaryResponse> deactivateUser(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable UUID id) {
        AdminUserSummaryResponse response = adminUserService.deactivateUser(admin != null ? admin.userId() : null, id);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/users/{id}/reset-password
     * Resets a user's password to a generated 16-character temporary password.
     */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable UUID id) {
        ResetPasswordResponse response = adminUserService.resetPassword(admin != null ? admin.userId() : null, id);
        return ResponseEntity.ok(response);
    }
}

