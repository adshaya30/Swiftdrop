package com.swiftdrop.parcel.lifecycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-PAR-011, BR-07: an admin cancel always carries a reason of 10-300 characters. */
public record AdminCancelRequest(@NotBlank @Size(min = 10, max = 300) String reason) { }
