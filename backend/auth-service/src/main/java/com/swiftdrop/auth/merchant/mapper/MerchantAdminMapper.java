package com.swiftdrop.auth.merchant.mapper;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.merchant.dto.AdminMerchantDetail;
import com.swiftdrop.auth.merchant.dto.AdminMerchantSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entities are never serialised: they are mapped to DTOs here. */
@Mapper(componentModel = "spring")
public interface MerchantAdminMapper {

    AdminMerchantSummary toSummary(Merchant merchant);

    @Mapping(target = "id", source = "merchant.id")
    @Mapping(target = "userId", source = "merchant.userId")
    @Mapping(target = "businessName", source = "merchant.businessName")
    @Mapping(target = "businessRegistrationNo", source = "merchant.businessRegistrationNo")
    @Mapping(target = "contactName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "addressLine1", source = "merchant.addressLine1")
    @Mapping(target = "city", source = "merchant.city")
    @Mapping(target = "status", source = "merchant.status")
    @Mapping(target = "statusReason", source = "merchant.statusReason")
    @Mapping(target = "discountPercent", source = "merchant.discountPercent")
    @Mapping(target = "decidedAt", source = "merchant.decidedAt")
    @Mapping(target = "decidedBy", source = "merchant.decidedBy")
    @Mapping(target = "createdAt", source = "merchant.createdAt")
    @Mapping(target = "updatedAt", source = "merchant.updatedAt")
    AdminMerchantDetail toDetail(Merchant merchant, User user);
}
