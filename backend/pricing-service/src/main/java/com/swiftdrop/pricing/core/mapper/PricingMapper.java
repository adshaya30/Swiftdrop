package com.swiftdrop.pricing.core.mapper;

import com.swiftdrop.pricing.core.dto.FeeBreakdown;
import com.swiftdrop.pricing.core.dto.response.*;
import com.swiftdrop.pricing.domain.District;
import com.swiftdrop.pricing.domain.SizeSurcharge;
import com.swiftdrop.pricing.domain.Zone;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PricingMapper {
    ZoneDto zone(Zone entity);
    DistrictDto district(District entity);
    SizeOptionDto size(SizeSurcharge entity);

    @Mapping(target = "zoneCode", source = "zone.code")
    @Mapping(target = "zoneName", source = "zone.name")
    DistrictOptionDto districtOption(District entity);

    @Mapping(target = "currency", constant = "LKR")
    @Mapping(target = "originZone", source = "origin.zone")
    @Mapping(target = "destinationZone", source = "destination.zone")
    @Mapping(target = "baseFee", source = "fees.baseFee")
    @Mapping(target = "weightFee", source = "fees.weightFee")
    @Mapping(target = "sizeSurcharge", source = "fees.sizeSurcharge")
    @Mapping(target = "discountAmount", source = "fees.discountAmount")
    @Mapping(target = "total", source = "fees.total")
    QuoteResponse quote(District origin, District destination, FeeBreakdown fees);

    @Mapping(target = "currency", constant = "LKR")
    @Mapping(target = "originZone", source = "origin.zone")
    @Mapping(target = "destinationZone", source = "destination.zone")
    @Mapping(target = "originDistrict", source = "origin")
    @Mapping(target = "destinationDistrict", source = "destination")
    @Mapping(target = "baseFee", source = "fees.baseFee")
    @Mapping(target = "weightFee", source = "fees.weightFee")
    @Mapping(target = "sizeSurcharge", source = "fees.sizeSurcharge")
    @Mapping(target = "discountAmount", source = "fees.discountAmount")
    @Mapping(target = "subtotal", source = "fees.subtotal")
    @Mapping(target = "total", source = "fees.total")
    InternalPricingResponse internal(District origin, District destination, FeeBreakdown fees, BigDecimal discountPercent);
}
