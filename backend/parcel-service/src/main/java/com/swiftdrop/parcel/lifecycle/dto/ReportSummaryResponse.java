package com.swiftdrop.parcel.lifecycle.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** FR-PAR-013: totals for an inclusive Asia/Colombo date range; rates are percent with one decimal. */
public record ReportSummaryResponse(LocalDate from, LocalDate to, long created, long delivered, long failed,
        long cancelled, long inProgress, BigDecimal successRate, BigDecimal failureRate) { }
