package com.swiftdrop.auth.merchant.mapper;

import com.swiftdrop.auth.merchant.dto.CredentialSummary;
import com.swiftdrop.auth.merchant.entity.MerchantCredential;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CredentialMapper {

    CredentialSummary toSummary(MerchantCredential credential);
}
