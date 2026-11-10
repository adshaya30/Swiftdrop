package com.swiftdrop.auth.merchant.mapper;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.merchant.dto.InternalMerchantView;
import com.swiftdrop.auth.merchant.dto.InternalUserContact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InternalMapper {

    InternalUserContact toContact(User user);

    @Mapping(target = "id", source = "merchant.id")
    @Mapping(target = "userId", source = "merchant.userId")
    @Mapping(target = "businessName", source = "merchant.businessName")
    @Mapping(target = "status", source = "merchant.status")
    @Mapping(target = "userStatus", source = "user.status")
    @Mapping(target = "discountPercent", source = "merchant.discountPercent")
    @Mapping(target = "contactEmail", source = "user.email")
    InternalMerchantView toMerchantView(Merchant merchant, User user);
}
