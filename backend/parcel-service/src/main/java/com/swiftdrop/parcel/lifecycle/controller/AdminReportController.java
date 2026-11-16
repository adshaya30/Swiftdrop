package com.swiftdrop.parcel.lifecycle.controller;

import com.swiftdrop.parcel.lifecycle.dto.DailyReportRow;
import com.swiftdrop.parcel.lifecycle.dto.DashboardParcelsResponse;
import com.swiftdrop.parcel.lifecycle.dto.ReportSummaryResponse;
import com.swiftdrop.parcel.lifecycle.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FR-PAR-013: admin reports and the parcels dashboard card. ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final AdminReportService reports;

    public AdminReportController(AdminReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/reports/summary")
    @Operation(summary = "Report totals and rates",
            description = "FR-PAR-013: from and to are inclusive Asia/Colombo dates (yyyy-MM-dd); default last 30 "
                    + "days, maximum 366.")
    public ReportSummaryResponse summary(@RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        return reports.summary(from, to);
    }

    @GetMapping("/reports/daily")
    @Operation(summary = "Report per Asia/Colombo day", description = "FR-PAR-013: one row per day, zero rows included.")
    public List<DailyReportRow> daily(@RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to) {
        return reports.daily(from, to);
    }

    @GetMapping("/dashboard/parcels")
    @Operation(summary = "Parcels dashboard card", description = "FR-PAR-013: {deliveredToday, failedToday}.")
    public DashboardParcelsResponse dashboard() {
        return reports.dashboard();
    }
}
