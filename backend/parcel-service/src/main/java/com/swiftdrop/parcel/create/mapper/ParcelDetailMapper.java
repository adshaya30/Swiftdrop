package com.swiftdrop.parcel.create.mapper;

import com.swiftdrop.parcel.create.dto.CreatedParcel;
import com.swiftdrop.parcel.create.dto.ParcelDetailDto;
import com.swiftdrop.parcel.create.dto.AdminParcelDetailDto;
import com.swiftdrop.parcel.create.dto.InternalParcelDetailDto;
import com.swiftdrop.parcel.domain.Parcel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Master 8.1: DTO mapping only; never serialize managed entities. */
@Mapper(componentModel = "spring")
public interface ParcelDetailMapper {
    @Mapping(target = "ownerType", expression = "java(p.getCustomerId() != null ? com.swiftdrop.common.enums.OwnerType.CUSTOMER : com.swiftdrop.common.enums.OwnerType.MERCHANT)")
    @Mapping(target = "parcel", source = "p")
    @Mapping(target = "fee", source = "p")
    ParcelDetailDto toDetail(Parcel p);

    /** Master 8.1: callers must authorize before mapping; this method grants no access. */
    @Mapping(target = "ownerType", expression = "java(p.getCustomerId() != null ? com.swiftdrop.common.enums.OwnerType.CUSTOMER : com.swiftdrop.common.enums.OwnerType.MERCHANT)")
    @Mapping(target = "parcel", source = "p")
    @Mapping(target = "fee", source = "p")
    AdminParcelDetailDto toAdminDetail(Parcel p);

    /** SRS 7.4: full admin snapshot plus statusSequence, for internal endpoint owners. */
    @Mapping(target = "ownerType", expression = "java(p.getCustomerId() != null ? com.swiftdrop.common.enums.OwnerType.CUSTOMER : com.swiftdrop.common.enums.OwnerType.MERCHANT)")
    @Mapping(target = "parcel", source = "p")
    @Mapping(target = "fee", source = "p")
    InternalParcelDetailDto toInternalDetail(Parcel p);

    @Mapping(target = "fee", source = "p")
    CreatedParcel toCreated(Parcel p);

    @Mapping(target = "baseFee", source = "feeBase")
    @Mapping(target = "weightFee", source = "feeWeight")
    @Mapping(target = "sizeSurcharge", source = "feeSize")
    @Mapping(target = "discountAmount", source = "feeDiscount")
    @Mapping(target = "total", source = "feeTotal")
    CreatedParcel.Fee toFee(Parcel p);

    ParcelDetailDto.ParcelData toParcelData(Parcel p);
}
