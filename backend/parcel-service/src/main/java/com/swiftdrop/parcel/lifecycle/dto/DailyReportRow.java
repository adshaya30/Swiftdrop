package com.swiftdrop.parcel.lifecycle.dto;

import java.time.LocalDate;

/** FR-PAR-013: one Asia/Colombo day; created by creation day, the others by the day of terminal_at. */
public record DailyReportRow(LocalDate date, long created, long delivered, long failed, long cancelled) { }
