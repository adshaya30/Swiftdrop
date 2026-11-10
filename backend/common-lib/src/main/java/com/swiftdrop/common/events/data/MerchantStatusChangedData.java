package com.swiftdrop.common.events.data;

import com.swiftdrop.common.enums.MerchantStatus;
import java.util.UUID;

/** Data of {@code merchant.status.changed} (SRS 3.5). Nullable: reason. */
public record MerchantStatusChangedData(
        UUID merchantId,
        String businessName,
        MerchantStatus previousStatus,
        MerchantStatus status,
        String reason) {
}
