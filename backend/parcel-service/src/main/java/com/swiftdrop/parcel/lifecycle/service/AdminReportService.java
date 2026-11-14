package com.swiftdrop.parcel.lifecycle.service;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.parcel.lifecycle.dto.DailyReportRow;
import com.swiftdrop.parcel.lifecycle.dto.DashboardParcelsResponse;
import com.swiftdrop.parcel.lifecycle.dto.ReportSummaryResponse;
import com.swiftdrop.parcel.lifecycle.repository.AdminReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-PAR-013 reports. Dates are inclusive Asia/Colombo dates (BR-18); the default range is the last 30 days and
 * the maximum is 366 days (more gives 400 VALIDATION_ERROR). Success rate = delivered / (delivered + failed) and
 * failure rate = failed / (delivered + failed) over parcels whose terminal_at is in range, percent with one
 * decimal, 0.0 when the denominator is 0.
 */
@Service
@Transactional(readOnly = true)
public class AdminReportService {

    static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final AdminReportRepository reports;
    private final Clock clock;

    @Autowired
    public AdminReportService(AdminReportRepository reports) {
        this(reports, Clock.system(COLOMBO));
    }

    AdminReportService(AdminReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    public ReportSummaryResponse summary(LocalDate from, LocalDate to) {
        Range range = resolve(from, to);
        Map<ParcelStatus, Long> terminal = terminalCounts(range.start(), range.end());
        long delivered = terminal.getOrDefault(ParcelStatus.DELIVERED, 0L);
        long failed = terminal.getOrDefault(ParcelStatus.FAILED, 0L);
        long finished = delivered + failed;
        return new ReportSummaryResponse(range.fromDate(), range.toDate(),
                reports.countCreated(range.start(), range.end()), delivered, failed,
                terminal.getOrDefault(ParcelStatus.CANCELLED, 0L),
                reports.countInProgress(range.start(), range.end()),
                percent(delivered, finished), percent(failed, finished));
    }

    /** One row per Asia/Colombo day of the range, zero rows included. */
    public List<DailyReportRow> daily(LocalDate from, LocalDate to) {
        Range range = resolve(from, to);
        Map<LocalDate, long[]> rows = new TreeMap<>();
        for (LocalDate day = range.fromDate(); !day.isAfter(range.toDate()); day = day.plusDays(1)) {
            rows.put(day, new long[4]);
        }
        reports.createdPerDay(range.start(), range.end())
                .forEach(c -> rows.get(LocalDate.parse(c.getBucket()))[0] = c.getTotal());
        reports.terminalPerDay(range.start(), range.end()).forEach(c -> {
            long[] row = rows.get(LocalDate.parse(c.getBucket()));
            switch (ParcelStatus.valueOf(c.getStatus())) {
                case DELIVERED -> row[1] = c.getTotal();
                case FAILED -> row[2] = c.getTotal();
                case CANCELLED -> row[3] = c.getTotal();
                default -> { }
            }
        });
        List<DailyReportRow> result = new ArrayList<>();
        rows.forEach((day, v) -> result.add(new DailyReportRow(day, v[0], v[1], v[2], v[3])));
        return result;
    }

    /** FR-PAR-013: deliveredToday and failedToday, counted by terminal_at in the current Colombo day. */
    public DashboardParcelsResponse dashboard() {
        LocalDate today = LocalDate.now(clock);
        Map<ParcelStatus, Long> terminal = terminalCounts(today.atStartOfDay(COLOMBO).toInstant(),
                today.plusDays(1).atStartOfDay(COLOMBO).toInstant());
        return new DashboardParcelsResponse(terminal.getOrDefault(ParcelStatus.DELIVERED, 0L),
                terminal.getOrDefault(ParcelStatus.FAILED, 0L));
    }

    private Map<ParcelStatus, Long> terminalCounts(Instant start, Instant end) {
        Map<ParcelStatus, Long> counts = new EnumMap<>(ParcelStatus.class);
        reports.countTerminalByStatus(start, end).forEach(c -> counts.put(c.getStatus(), c.getTotal()));
        return counts;
    }

    private static BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return new BigDecimal("0.0");
        }
        return BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private Range resolve(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "The date range must be valid and at most " + MAX_DAYS + " days");
        }
        return new Range(start, end, start.atStartOfDay(COLOMBO).toInstant(),
                end.plusDays(1).atStartOfDay(COLOMBO).toInstant());
    }

    private record Range(LocalDate fromDate, LocalDate toDate, Instant start, Instant end) { }
}
