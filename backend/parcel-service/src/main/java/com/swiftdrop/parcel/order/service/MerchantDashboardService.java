package com.swiftdrop.parcel.order.service;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.order.dto.MerchantDashboardSummary;
import com.swiftdrop.parcel.order.repository.MerchantDashboardRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-PAR-014: order counts by status and the 10 most recent orders of one merchant. */
@Service
@Transactional(readOnly = true)
public class MerchantDashboardService {

    static final List<ParcelStatus> ACTIVE = List.of(ParcelStatus.CREATED, ParcelStatus.PICKED_UP,
            ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
    static final Duration RECENT_WINDOW = Duration.ofDays(30);

    private final MerchantDashboardRepository repository;
    private final Clock clock;

    @Autowired
    public MerchantDashboardService(MerchantDashboardRepository repository) {
        this(repository, Clock.systemUTC());
    }

    MerchantDashboardService(MerchantDashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public MerchantDashboardSummary summary(UUID merchantId) {
        Map<ParcelStatus, Long> active = zeros(ACTIVE);
        repository.countByStatus(merchantId, ACTIVE).forEach(c -> active.put(c.getStatus(), c.getTotal()));

        Map<ParcelStatus, Long> recent = zeros(Arrays.asList(ParcelStatus.values()));
        repository.countCreatedSince(merchantId, clock.instant().minus(RECENT_WINDOW))
                .forEach(c -> recent.put(c.getStatus(), c.getTotal()));

        List<MerchantDashboardSummary.RecentOrder> orders = repository
                .findTop10ByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(p -> new MerchantDashboardSummary.RecentOrder(p.getId(), p.getTrackingNumber(), p.getStatus(),
                        p.getMerchantReference(), p.getDropoff().getCity(), p.getCreatedAt()))
                .toList();
        return new MerchantDashboardSummary(Collections.unmodifiableMap(active), Collections.unmodifiableMap(recent),
                orders);
    }

    private static Map<ParcelStatus, Long> zeros(List<ParcelStatus> statuses) {
        Map<ParcelStatus, Long> map = new EnumMap<>(ParcelStatus.class);
        statuses.forEach(s -> map.put(s, 0L));
        return map;
    }
}
