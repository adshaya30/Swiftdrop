package com.swiftdrop.parcel.create.service;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.error.*;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.parcel.create.client.PricingClient;
import com.swiftdrop.parcel.create.dto.*;
import com.swiftdrop.parcel.create.mapper.ParcelDetailMapper;
import com.swiftdrop.parcel.domain.ParcelRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("TST-021")
class ParcelCreationServiceTests {
    private final IdempotencyService keys = mock(IdempotencyService.class);
    private final PricingClient pricing = mock(PricingClient.class);
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ParcelCreationService service = new DefaultParcelCreationService(keys, pricing,
            mock(TrackingNumberGenerator.class), parcels, outbox, mock(ParcelDetailMapper.class), manager);

    private CreationRequest request(Supplier<BigDecimal> discount) {
        return new CreationRequest(OwnerType.CUSTOMER, UUID.randomUUID(), null, new BigDecimal("800"),
                null, "key", "a".repeat(64), discount, CustomerParcelCreatedResponse::from);
    }

    @Test
    void AT09ReplayPrecedesEvenDiscountEligibilityResolution() {
        var expected = new CreationResult(201, Map.of("total", 800), true, UUID.randomUUID());
        when(keys.findReplay(any(), any(), any(), any())).thenReturn(Optional.of(expected));
        assertThat(service.create(request(() -> { throw new AssertionError("resolver must not run"); })))
                .isSameAs(expected);
        verifyNoInteractions(pricing, parcels, outbox, manager);
    }

    @Test
    void AT08FeeMismatchReturnsAuthoritativeFeeWithoutOpeningWriteTransaction() {
        when(keys.findReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(pricing.calculate(any(), any())).thenReturn(new PricingClient.Calculation("LKR", null, null,
                new BigDecimal("600"), new BigDecimal("150"), new BigDecimal("150"),
                BigDecimal.ZERO, new BigDecimal("900"), null, null, new BigDecimal("900"), BigDecimal.ZERO));
        assertThatThrownBy(() -> service.create(request(() -> BigDecimal.ZERO)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.FEE_CHANGED);
                    assertThat(((CreatedParcel.Fee) e.extensions().get("fee")).total()).isEqualByComparingTo("900");
                });
        verifyNoInteractions(parcels, outbox, manager);
    }

    @Test
    void pricingBusinessErrorIsPreservedAndNothingIsWritten() {
        when(keys.findReplay(any(), any(), any(), any())).thenReturn(Optional.empty());
        var failure = new DomainException(ErrorCode.ZONE_NOT_SERVICEABLE, "Zone unavailable");
        when(pricing.calculate(any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> service.create(request(() -> BigDecimal.ZERO))).isSameAs(failure);
        verifyNoInteractions(parcels, outbox, manager);
    }
}
