package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.create.service.ParcelCreationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CreationContractTests {
    @Test
    void merchantCallerCanCompileAgainstMasterSignatureWithoutPersistence() {
        UUID parcelId = UUID.randomUUID();
        var fee = new CreatedParcel.Fee("LKR", BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("9.00"));
        var created = new CreatedParcel(parcelId, "SD-0000000001", ParcelStatus.CREATED,
                "REF-1", fee, Instant.EPOCH);
        var request = new CreationRequest(OwnerType.MERCHANT, UUID.randomUUID(), null, null,
                "REF-1", "key", "hash", () -> BigDecimal.TEN,
                parcel -> java.util.Map.of("id", parcel.id(), "fee", parcel.fee(),
                        "trackingUrl", "/track/" + parcel.trackingNumber()));
        ParcelCreationService contract = input ->
                new CreationResult(201, input.responseBuilder().apply(created), false, created.id());
        var result = contract.create(request);
        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.parcelId()).isEqualTo(parcelId);
        assertThat(result.replay()).isFalse();
        assertThat(result.body()).isInstanceOf(java.util.Map.class);
        assertThat(request.discountPercentResolver().get()).isEqualByComparingTo("10");
        assertThat(created.merchantReference()).isEqualTo("REF-1");
        assertThat(created.createdAt()).isEqualTo(Instant.EPOCH);
    }
}
