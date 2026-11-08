package com.swiftdrop.pricing.core.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DistrictsResponse(BigDecimal maxWeightKg, List<SizeOptionDto> sizes, List<DistrictOptionDto> districts) { }
