package com.swiftdrop.pricing.admin.dto;

import java.util.List;
import java.util.UUID;

/** FR-PRC-006: a zone with its districts. */
public record ZoneAdminResponse(UUID id, String code, String name, boolean active, List<ZoneDistrictDto> districts) {
    public record ZoneDistrictDto(String code, String name) { }
}
