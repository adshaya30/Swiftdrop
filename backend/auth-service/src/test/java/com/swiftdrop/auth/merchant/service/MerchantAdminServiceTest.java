package com.swiftdrop.auth.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import com.swiftdrop.auth.merchant.mapper.MerchantAdminMapper;
import com.swiftdrop.auth.merchant.repository.MerchantAdminRepository;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("TST-029")
class MerchantAdminServiceTest {

    static final UUID ADMIN = UUID.randomUUID();
    static final String REASON = "Documents could not be verified";

    @Mock MerchantAdminRepository merchantRepository;
    @Mock UserRepository userRepository;
    @Mock OutboxService outboxService;
    @Mock AuditEvents auditEvents;
    @Mock RefreshTokenService refreshTokenService;

    MerchantAdminService service;
    Merchant merchant;
    UUID merchantId;
    UUID userId;

    @BeforeEach
    void setUp() {
        service = new MerchantAdminService(merchantRepository, userRepository,
                Mappers.getMapper(MerchantAdminMapper.class), outboxService, auditEvents, refreshTokenService);
        merchantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        merchant = new Merchant(userId, "Lanka Crafts", MerchantStatus.PENDING);
        merchant.setId(merchantId);
        User user = new User();
        user.setId(userId);
        user.setFullName("Nimal Perera");
        user.setEmail("shop@example.com");
        user.setPhone("+94771234567");
        when(merchantRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    /** The five actions as (name, call). */
    private enum Action {
        APPROVE(MerchantStatus.APPROVED, "MERCHANT_APPROVED", false),
        REJECT(MerchantStatus.REJECTED, "MERCHANT_REJECTED", true),
        SUSPEND(MerchantStatus.SUSPENDED, "MERCHANT_SUSPENDED", true),
        REINSTATE(MerchantStatus.APPROVED, "MERCHANT_REINSTATED", false);

        final MerchantStatus target;
        final String audit;
        final boolean needsReason;

        Action(MerchantStatus target, String audit, boolean needsReason) {
            this.target = target;
            this.audit = audit;
            this.needsReason = needsReason;
        }

        boolean allowedFrom(MerchantStatus from) {
            return switch (this) {
                case APPROVE -> from == MerchantStatus.PENDING || from == MerchantStatus.REJECTED;
                case REJECT -> from == MerchantStatus.PENDING;
                case SUSPEND -> from == MerchantStatus.APPROVED;
                case REINSTATE -> from == MerchantStatus.SUSPENDED;
            };
        }
    }

    private void run(Action action, String reason) {
        switch (action) {
            case APPROVE -> service.approve(merchantId, ADMIN);
            case REJECT -> service.reject(merchantId, ADMIN, reason);
            case SUSPEND -> service.suspend(merchantId, ADMIN, reason);
            case REINSTATE -> service.reinstate(merchantId, ADMIN);
        }
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    @DisplayName("TST-029 BR-12: every action is allowed from exactly its source states, all others give 409")
    void transitionMatrix(Action action) {
        for (MerchantStatus from : MerchantStatus.values()) {
            merchant.setStatus(from);
            org.mockito.Mockito.clearInvocations(outboxService, auditEvents, refreshTokenService);
            if (action.allowedFrom(from)) {
                run(action, REASON);
                assertThat(merchant.getStatus()).as(action + " from " + from).isEqualTo(action.target);
            } else {
                assertThatThrownBy(() -> run(action, REASON))
                        .as(action + " from " + from)
                        .isInstanceOfSatisfying(DomainException.class,
                                e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
                assertThat(merchant.getStatus()).isEqualTo(from);
                verify(outboxService, never()).add(any(), any());
                verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    @DisplayName("TST-029 FR-AUTH-017: each status action sets decided_at and decided_by and writes event + audit")
    void writesEventAndAudit(Action action) {
        merchant.setStatus(switch (action) {
            case APPROVE, REJECT -> MerchantStatus.PENDING;
            case SUSPEND -> MerchantStatus.APPROVED;
            case REINSTATE -> MerchantStatus.SUSPENDED;
        });
        MerchantStatus previous = merchant.getStatus();

        run(action, REASON);

        assertThat(merchant.getDecidedBy()).isEqualTo(ADMIN);
        assertThat(merchant.getDecidedAt()).isNotNull();
        assertThat(merchant.getStatusReason()).isEqualTo(action.needsReason ? REASON : null);
        verify(merchantRepository).save(merchant);

        ArgumentCaptor<MerchantStatusChangedData> data = ArgumentCaptor.forClass(MerchantStatusChangedData.class);
        verify(outboxService).add(eq(EventTypes.MERCHANT_STATUS_CHANGED), data.capture());
        assertThat(data.getValue().merchantId()).isEqualTo(merchantId);
        assertThat(data.getValue().businessName()).isEqualTo("Lanka Crafts");
        assertThat(data.getValue().previousStatus().name()).isEqualTo(previous.name());
        assertThat(data.getValue().status().name()).isEqualTo(action.target.name());
        assertThat(data.getValue().reason()).isEqualTo(action.needsReason ? REASON : null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditEvents).record(eq(ADMIN), eq(ActorRole.ADMIN), eq(action.audit), eq("MERCHANT"),
                eq(merchantId.toString()), details.capture());
        // FR-AUTH-023 / BR-28: statuses only, no reason text, no contact data
        assertThat(details.getValue()).containsOnlyKeys("previousStatus", "status");
    }

    @Test
    @DisplayName("TST-029 BR-23: suspend revokes all refresh tokens of the merchant's user, other actions do not")
    void suspendRevokesTokens() {
        merchant.setStatus(MerchantStatus.APPROVED);
        service.suspend(merchantId, ADMIN, REASON);
        verify(refreshTokenService).revokeAllForUser(userId);

        merchant.setStatus(MerchantStatus.PENDING);
        org.mockito.Mockito.clearInvocations(refreshTokenService);
        service.approve(merchantId, ADMIN);
        merchant.setStatus(MerchantStatus.PENDING);
        service.reject(merchantId, ADMIN, REASON);
        merchant.setStatus(MerchantStatus.SUSPENDED);
        service.reinstate(merchantId, ADMIN);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "too short", "123456789"})
    @DisplayName("TST-029 BR-12: reject and suspend need a reason of at least 10 characters")
    void reasonTooShort(String reason) {
        merchant.setStatus(MerchantStatus.PENDING);
        assertThatThrownBy(() -> service.reject(merchantId, ADMIN, reason)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        merchant.setStatus(MerchantStatus.APPROVED);
        assertThatThrownBy(() -> service.suspend(merchantId, ADMIN, reason)).isInstanceOf(DomainException.class);
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        verify(outboxService, never()).add(any(), any());
    }

    @Test
    @DisplayName("TST-029 BR-12: reason bounds 10 and 300 characters, measured after trimming")
    void reasonBounds() {
        merchant.setStatus(MerchantStatus.PENDING);
        service.reject(merchantId, ADMIN, "  " + "x".repeat(10) + "  ");
        assertThat(merchant.getStatusReason()).isEqualTo("x".repeat(10));

        merchant.setStatus(MerchantStatus.PENDING);
        service.reject(merchantId, ADMIN, "y".repeat(300));
        assertThat(merchant.getStatusReason()).hasSize(300);

        merchant.setStatus(MerchantStatus.PENDING);
        assertThatThrownBy(() -> service.reject(merchantId, ADMIN, "z".repeat(301)))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "25.5", "49.99", "50", "50.00"})
    @DisplayName("TST-029 FR-AUTH-017: discount 0.00-50.00 is stored with two decimals and audited")
    void discountAccepted(String value) {
        merchant.setStatus(MerchantStatus.APPROVED);
        var detail = service.setDiscount(merchantId, ADMIN, new BigDecimal(value));

        assertThat(merchant.getDiscountPercent()).isEqualByComparingTo(value);
        assertThat(merchant.getDiscountPercent().scale()).isEqualTo(2);
        assertThat(detail.discountPercent()).isEqualByComparingTo(value);
        verify(auditEvents).record(eq(ADMIN), eq(ActorRole.ADMIN), eq("MERCHANT_DISCOUNT_CHANGED"), eq("MERCHANT"),
                eq(merchantId.toString()), any());
        // not a status change
        verify(outboxService, never()).add(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "50.01", "51", "12.345", "100"})
    @DisplayName("TST-029 FR-AUTH-017: discount outside 0-50 or with more than two decimals gives 400")
    void discountRejected(String value) {
        assertThatThrownBy(() -> service.setDiscount(merchantId, ADMIN, new BigDecimal(value)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(merchant.getDiscountPercent()).isEqualByComparingTo("0");
        verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-017: unknown merchant gives 404 NOT_FOUND for every action")
    void unknownMerchant() {
        UUID other = UUID.randomUUID();
        List<Consumer<UUID>> calls = List.of(
                id -> service.approve(id, ADMIN),
                id -> service.reject(id, ADMIN, REASON),
                id -> service.suspend(id, ADMIN, REASON),
                id -> service.reinstate(id, ADMIN),
                id -> service.setDiscount(id, ADMIN, BigDecimal.TEN),
                id -> service.get(id));
        for (Consumer<UUID> call : calls) {
            assertThatThrownBy(() -> call.accept(other)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-017: list validates page, size, status filter and sort field")
    void listValidation() {
        for (Runnable bad : new Runnable[] {
                () -> service.list(null, null, -1, 20, null),
                () -> service.list(null, null, 0, 0, null),
                () -> service.list(null, null, 0, 101, null),
                () -> service.list("NOPE", null, 0, 20, null),
                () -> service.list(null, null, 0, 20, "password,asc"),
                () -> service.list(null, null, 0, 20, "createdAt,sideways")}) {
            assertThatThrownBy(bad::run).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }
}
