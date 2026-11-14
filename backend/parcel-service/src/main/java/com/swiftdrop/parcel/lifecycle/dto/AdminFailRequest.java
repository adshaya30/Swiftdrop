package com.swiftdrop.parcel.lifecycle.dto;

import com.swiftdrop.common.enums.FailureReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** FR-PAR-012, BR-16: the reason comes from the Table 2.4 admin override row; the note is mandatory (5-500). */
public record AdminFailRequest(@NotNull FailureReason failureReason, @NotBlank @Size(min = 5, max = 500) String note) { }
