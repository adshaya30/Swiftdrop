package com.swiftdrop.parcel.lifecycle.service;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.parcel.create.dto.AdminParcelDetailDto;
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

/**
 * FR-PAR-011: admin search over all parcels (read only). Filters are combined with JPA Specifications, so every
 * value is a bound parameter; sorting is limited to a whitelist and the page size to 100 (BR-26, FR-PAR-009).
 */
@Service
@Transactional(readOnly = true)
public class AdminParcelQueryService {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "status", "trackingNumber");

    private final ParcelRepository parcels;
    private final ParcelDetailMapper mapper;

    public AdminParcelQueryService(ParcelRepository parcels, ParcelDetailMapper mapper) {
        this.parcels = parcels;
        this.mapper = mapper;
    }

    /** FR-PAR-011: full admin detail; an unknown id answers 404 NOT_FOUND. */
    public AdminParcelDetailDto detail(UUID id) {
        return parcels.findById(id)
                .map(mapper::toAdminDetail)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));
    }

    /**
     * @param from inclusive Asia/Colombo date (BR-18) of the creation day
     * @param q tracking number or merchant reference prefix (literal, LIKE wildcards escaped)
     * @param originDistrictCode district code of the pickup address
     * @param destinationDistrictCode district code of the drop-off address
     */
    public Pagination<AdminParcelDetailDto> list(ParcelStatus status, OwnerType ownerType, LocalDate from,
                                                 LocalDate to, String q, String originDistrictCode,
                                                 String destinationDistrictCode, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))) {
            throw invalid();
        }
        String[] ordering = sort.split(",", -1);
        if (ordering.length != 2 || !SORT_FIELDS.contains(ordering[0]) || !Set.of("asc", "desc").contains(ordering[1])) {
            throw invalid();
        }
        Specification<Parcel> filter = Specification.where(null);
        if (status != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (ownerType == OwnerType.CUSTOMER) {
            filter = filter.and((root, query, cb) -> cb.isNotNull(root.get("customerId")));
        } else if (ownerType == OwnerType.MERCHANT) {
            filter = filter.and((root, query, cb) -> cb.isNotNull(root.get("merchantId")));
        }
        if (from != null) {
            filter = filter.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(COLOMBO).toInstant()));
        }
        if (to != null) {
            filter = filter.and((root, query, cb) ->
                    cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay(COLOMBO).toInstant()));
        }
        if (q != null && !q.isBlank()) {
            String prefix = q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filter = filter.and((root, query, cb) -> cb.or(
                    cb.like(root.get("trackingNumber"), prefix, '\\'),
                    cb.like(root.get("merchantReference"), prefix, '\\')));
        }
        if (originDistrictCode != null && !originDistrictCode.isBlank()) {
            filter = filter.and((root, query, cb) ->
                    cb.equal(root.get("pickup").get("districtCode"), originDistrictCode));
        }
        if (destinationDistrictCode != null && !destinationDistrictCode.isBlank()) {
            filter = filter.and((root, query, cb) ->
                    cb.equal(root.get("dropoff").get("districtCode"), destinationDistrictCode));
        }
        var result = parcels.findAll(filter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(ordering[1]), ordering[0])));
        return Pagination.of(result.getContent().stream().map(mapper::toAdminDetail).toList(), page, size,
                result.getTotalElements());
    }

    private static DomainException invalid() {
        return new DomainException(ErrorCode.VALIDATION_ERROR, "Invalid parcel list parameters");
    }
}
