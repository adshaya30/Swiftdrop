package com.swiftdrop.delivery.assignment.repository;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.delivery.assignment.dto.CourierWorkloadResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourierWorkloadQueries {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public CourierWorkloadQueries(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public List<CourierWorkloadResponse> listWorkloads() {
        LocalDate today = LocalDate.now(clock.withZone(COLOMBO));
        Instant dayStart = today.atStartOfDay(COLOMBO).toInstant();
        Instant nextDayStart = today.plusDays(1).atStartOfDay(COLOMBO).toInstant();
        String sql = """
                select c.id, c.full_name, c.vehicle_type, c.vehicle_number, c.status,
                       count(d.parcel_id) filter (where d.status = 'CREATED') as created_count,
                       count(d.parcel_id) filter (where d.status = 'PICKED_UP') as picked_up_count,
                       count(d.parcel_id) filter (where d.status = 'IN_TRANSIT') as in_transit_count,
                       count(d.parcel_id) filter (where d.status = 'OUT_FOR_DELIVERY') as out_for_delivery_count,
                       count(d.parcel_id) filter (where d.status = 'DELIVERED'
                           and d.terminal_at >= ? and d.terminal_at < ?) as delivered_today,
                       count(d.parcel_id) filter (where d.status = 'FAILED'
                           and d.terminal_at >= ? and d.terminal_at < ?) as failed_today
                from couriers c left join deliveries d on d.courier_id = c.id
                group by c.id, c.full_name, c.vehicle_type, c.vehicle_number, c.status
                order by (count(d.parcel_id) filter (where d.status in
                    ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'))) asc, c.id asc
                """;
        return jdbcTemplate.query(sql, (result, row) -> {
            Map<String, Long> activeCounts = new LinkedHashMap<>();
            activeCounts.put(ParcelStatus.CREATED.name(), result.getLong("created_count"));
            activeCounts.put(ParcelStatus.PICKED_UP.name(), result.getLong("picked_up_count"));
            activeCounts.put(ParcelStatus.IN_TRANSIT.name(), result.getLong("in_transit_count"));
            activeCounts.put(ParcelStatus.OUT_FOR_DELIVERY.name(), result.getLong("out_for_delivery_count"));
            return new CourierWorkloadResponse(
                    result.getObject("id", UUID.class), result.getString("full_name"),
                    VehicleType.valueOf(result.getString("vehicle_type")), result.getString("vehicle_number"),
                    UserStatus.valueOf(result.getString("status")), Map.copyOf(activeCounts),
                    result.getLong("delivered_today"), result.getLong("failed_today"));
        }, Timestamp.from(dayStart), Timestamp.from(nextDayStart), Timestamp.from(dayStart),
                Timestamp.from(nextDayStart));
    }

    public long countActiveForCourier(UUID courierId) {
        String sql = "select count(*) from deliveries where courier_id = ? and status in (?, ?, ?)";
        return jdbcTemplate.queryForObject(sql, Long.class, courierId, ParcelStatus.PICKED_UP.name(),
                ParcelStatus.IN_TRANSIT.name(), ParcelStatus.OUT_FOR_DELIVERY.name());
    }
}