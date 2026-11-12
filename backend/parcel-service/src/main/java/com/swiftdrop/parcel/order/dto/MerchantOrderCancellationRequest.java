package com.swiftdrop.parcel.order.dto;

import jakarta.validation.constraints.Size;

/** FR-PAR-008, BR-07: the owner's cancellation reason is optional, at most 300 characters. */
public record MerchantOrderCancellationRequest(@Size(max = 300) String reason) {
}
