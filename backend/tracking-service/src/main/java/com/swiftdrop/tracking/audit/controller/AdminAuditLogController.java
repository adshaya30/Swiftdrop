package com.swiftdrop.tracking.audit.controller;

import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.tracking.audit.dto.AuditLogResponse;
import com.swiftdrop.tracking.audit.service.AuditLogQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FR-TRK-005: ADMIN only; other roles get 403 and client-credentials tokens are refused by common-lib. */
@RestController
@Validated
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AuditLogQueryService service;

    public AdminAuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    /**
     * Search the audit log. Filters: action, actorId, from, to (inclusive yyyy-MM-dd dates in Asia/Colombo);
     * newest first; page is zero-based, size 1-100.
     */
    @GetMapping
    public Pagination<AuditLogResponse> list(@RequestParam(required = false) @Size(max = 60) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(action, actorId, from, to, page, size);
    }
}
