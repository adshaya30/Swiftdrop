package com.swiftdrop.pricing.domain;

import com.swiftdrop.common.enums.ParcelSize;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SizeSurchargeRepository extends JpaRepository<SizeSurcharge, ParcelSize> {

}
