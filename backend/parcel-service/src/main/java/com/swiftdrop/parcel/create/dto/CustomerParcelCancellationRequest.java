package com.swiftdrop.parcel.create.dto;

import jakarta.validation.constraints.Size;

/** FR-PAR-009: owner cancellation reason is optional. */
public record CustomerParcelCancellationRequest(@Size(max = 300) String reason) {}
