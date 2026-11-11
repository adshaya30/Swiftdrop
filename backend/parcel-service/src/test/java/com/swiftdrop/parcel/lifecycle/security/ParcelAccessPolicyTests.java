package com.swiftdrop.parcel.lifecycle.security;

import com.swiftdrop.common.enums.Role;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.ParcelFixtures;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelAccessPolicyTests {

    @Mock
    private ParcelRepository parcelRepository;

    private ParcelAccessPolicy accessPolicy;

    private final UUID parcelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accessPolicy = new ParcelAccessPolicy(parcelRepository);
    }

    @Test
    @Tag("TST-033")
    @DisplayName("FR-PAR-017 BR-25: non-existent parcel returns 404 NOT_FOUND")
    void nonExistentParcelReturns404() {
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.empty());

        CurrentUser user = new CurrentUser(customerId, Role.CUSTOMER, "user", null, Set.of(), false);

        assertThatThrownBy(() -> accessPolicy.requireAccessible(parcelId, user))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @Tag("TST-033")
    @DisplayName("FR-PAR-017 BR-25: customer owner matching customer_id is allowed")
    void customerOwnerAllowed() {
        Parcel parcel = ParcelFixtures.parcel("LP000000001LK", customerId, null, null);
        ReflectionTestUtils.setField(parcel, "id", parcelId);
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        CurrentUser user = new CurrentUser(customerId, Role.CUSTOMER, "user", null, Set.of(), false);

        Parcel result = accessPolicy.requireAccessible(parcelId, user);
        assertThat(result).isSameAs(parcel);
    }

    @Test
    @Tag("TST-033")
    @DisplayName("FR-PAR-017 BR-25: different customer returns 404 NOT_FOUND")
    void differentCustomerReturns404() {
        Parcel parcel = ParcelFixtures.parcel("LP000000001LK", customerId, null, null);
        ReflectionTestUtils.setField(parcel, "id", parcelId);
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        CurrentUser otherUser = new CurrentUser(UUID.randomUUID(), Role.CUSTOMER, "user", null, Set.of(), false);

        assertThatThrownBy(() -> accessPolicy.requireAccessible(parcelId, otherUser))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @Tag("TST-033")
    @DisplayName("FR-PAR-017 BR-25: merchant owner matching mid is allowed")
    void merchantOwnerAllowed() {
        Parcel parcel = ParcelFixtures.parcel("LP000000002LK", null, merchantId, "REF-100");
        ReflectionTestUtils.setField(parcel, "id", parcelId);
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        CurrentUser merchantUser = new CurrentUser(UUID.randomUUID(), Role.MERCHANT, "user", merchantId, Set.of(), false);

        Parcel result = accessPolicy.requireAccessible(parcelId, merchantUser);
        assertThat(result).isSameAs(parcel);
    }

    @Test
    @Tag("TST-033")
    @DisplayName("FR-PAR-017 BR-25: different merchant returns 404 NOT_FOUND")
    void differentMerchantReturns404() {
        Parcel parcel = ParcelFixtures.parcel("LP000000002LK", null, merchantId, "REF-100");
        ReflectionTestUtils.setField(parcel, "id", parcelId);
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        CurrentUser otherMerchant = new CurrentUser(UUID.randomUUID(), Role.MERCHANT, "user", UUID.randomUUID(), Set.of(), false);

        assertThatThrownBy(() -> accessPolicy.requireAccessible(parcelId, otherMerchant))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @Tag("TST-033")
    @DisplayName("FR-PAR-017 BR-25: ADMIN always has access to any parcel")
    void adminAlwaysAllowed() {
        Parcel parcel = ParcelFixtures.parcel("LP000000003LK", customerId, null, null);
        ReflectionTestUtils.setField(parcel, "id", parcelId);
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        CurrentUser adminUser = new CurrentUser(UUID.randomUUID(), Role.ADMIN, "user", null, Set.of(), false);

        Parcel result = accessPolicy.requireAccessible(parcelId, adminUser);
        assertThat(result).isSameAs(parcel);
    }
}

