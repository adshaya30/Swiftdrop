package com.swiftdrop.pricing.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** FR-PRC-006: a district belongs to exactly one zone. */
public record MoveDistrictRequest(@NotNull UUID zoneId) { }
