package com.swiftdrop.pricing.admin.dto;

import jakarta.validation.constraints.Size;

/** FR-PRC-006: both members are optional but at least one is required (checked by the service). */
public record UpdateZoneRequest(@Size(min = 2, max = 60) String name, Boolean active) { }
