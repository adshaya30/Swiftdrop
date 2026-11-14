package com.swiftdrop.parcel.create.service;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.parcel.create.dto.ParcelDetailDto;
import com.swiftdrop.parcel.create.mapper.ParcelDetailMapper;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-PAR-009, BR-18/25/26: owner restriction is part of every database query. */
@Service
@Transactional(readOnly = true)
public class CustomerParcelQueryService {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private final ParcelRepository parcels;
    private final ParcelDetailMapper mapper;

    public CustomerParcelQueryService(ParcelRepository parcels, ParcelDetailMapper mapper) {
        this.parcels = parcels;
        this.mapper = mapper;
    }

    public ParcelDetailDto detail(UUID owner, UUID id) {
        return parcels.findOne(owned(owner).and((root, query, cb) -> cb.equal(root.get("id"), id)))
                .map(mapper::toDetail).orElseThrow(() ->
                        new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));
    }

    public Pagination<ParcelDetailDto> list(UUID owner, ParcelStatus status, LocalDate from, LocalDate to,
                                            String q, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))) {
            throw invalid();
        }
        String[] ordering = sort.split(",", -1);
        if (ordering.length != 2 || !Set.of("createdAt", "status", "trackingNumber").contains(ordering[0])
                || !Set.of("asc", "desc").contains(ordering[1])) throw invalid();
        Specification<Parcel> filter = owned(owner);
        if (status != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (from != null) filter = filter.and((root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(COLOMBO).toInstant()));
        if (to != null) filter = filter.and((root, query, cb) ->
                cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay(COLOMBO).toInstant()));
        if (q != null) {
            // Escape LIKE metacharacters: q is a literal tracking-number prefix.
            String prefix = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filter = filter.and((root, query, cb) -> cb.like(root.get("trackingNumber"), prefix, '\\'));
        }
        var result = parcels.findAll(filter, PageRequest.of(page, size,
                Sort.by(Sort.Direction.fromString(ordering[1]), ordering[0])));
        return Pagination.of(result.getContent().stream().map(mapper::toDetail).toList(), page, size,
                result.getTotalElements());
    }

    private static Specification<Parcel> owned(UUID owner) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), owner);
    }

    private static DomainException invalid() {
        return new DomainException(ErrorCode.VALIDATION_ERROR, "Invalid parcel list parameters");
    }
}
