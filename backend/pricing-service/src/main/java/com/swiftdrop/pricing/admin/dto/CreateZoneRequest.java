package com.swiftdrop.pricing.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** FR-PRC-006: the zone code matches [A-Z_]{2,20} and is immutable once created. */
public record CreateZoneRequest(
        @NotNull @Pattern(regexp = "[A-Z_]{2,20}", message = "must match [A-Z_]{2,20}") String code,
        @NotNull @Size(min = 2, max = 60) String name) { }
