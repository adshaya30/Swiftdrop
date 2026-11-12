package com.swiftdrop.parcel.order.service;

import com.swiftdrop.common.enums.MerchantStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.parcel.order.client.AuthClient;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * BR-12, BR-23, FR-PAR-007/008: a merchant may create or cancel orders only while it is APPROVED and its user is
 * ACTIVE. The check asks auth-service on every create and cancel, because an access token stays valid for up to
 * 15 minutes after a suspension (D-11).
 */
@Service
public class MerchantEligibility {

    private final AuthClient auth;

    public MerchantEligibility(AuthClient auth) {
        this.auth = auth;
    }

    /** @return the merchant's discount percent (BR-13), used for pricing */
    public BigDecimal requireActive(UUID merchantId) {
        AuthClient.Merchant merchant;
        try {
            merchant = auth.merchant(merchantId);
        } catch (DomainException e) {
            if (e.code() == ErrorCode.NOT_FOUND) {
                throw notActive();   // the token names a merchant auth-service no longer knows
            }
            throw e;
        }
        if (merchant.status() != MerchantStatus.APPROVED || merchant.userStatus() != UserStatus.ACTIVE) {
            throw notActive();
        }
        return merchant.discountPercent() == null ? BigDecimal.ZERO : merchant.discountPercent();
    }

    private static DomainException notActive() {
        return new DomainException(ErrorCode.MERCHANT_NOT_ACTIVE, "The merchant account is not active");
    }
}
