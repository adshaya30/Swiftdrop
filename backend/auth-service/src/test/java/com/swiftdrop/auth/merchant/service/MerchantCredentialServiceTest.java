package com.swiftdrop.auth.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.merchant.entity.MerchantCredential;
import com.swiftdrop.auth.merchant.mapper.CredentialMapper;
import com.swiftdrop.auth.merchant.repository.MerchantAdminRepository;
import com.swiftdrop.auth.merchant.repository.MerchantCredentialRepository;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.CredentialStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.events.AuditEvents;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("TST-029")
class MerchantCredentialServiceTest {

    @Mock MerchantRepository merchantRepository;
    @Mock MerchantAdminRepository merchantLockRepository;
    @Mock MerchantCredentialRepository credentialRepository;
    @Mock AuditEvents auditEvents;

    MerchantCredentialService service;
    UUID userId = UUID.randomUUID();
    Merchant merchant;

    @BeforeEach
    void setUp() {
        service = new MerchantCredentialService(merchantRepository, merchantLockRepository, credentialRepository,
                Mappers.getMapper(CredentialMapper.class), auditEvents);
        merchant = new Merchant(userId, "Lanka Crafts", MerchantStatus.APPROVED);
        merchant.setId(UUID.randomUUID());
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(merchantLockRepository.findByIdForUpdate(merchant.getId())).thenReturn(Optional.of(merchant));
        when(credentialRepository.countByMerchantIdAndStatus(merchant.getId(), CredentialStatus.ACTIVE)).thenReturn(0L);
        when(credentialRepository.saveAndFlush(any(MerchantCredential.class))).thenAnswer(i -> withId(i.getArgument(0)));
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-018: the secret is returned once, only its SHA-256 hash is stored, the row is locked")
    void createStoresOnlyTheHash() {
        var created = service.create(userId, "  production  ");

        assertThat(created.clientSecret()).matches("^sds_[A-Za-z0-9_-]{43}$");
        assertThat(created.clientId()).matches("^sdc_[A-Za-z0-9]{24}$");
        assertThat(created.label()).isEqualTo("production");
        assertThat(created.status()).isEqualTo(CredentialStatus.ACTIVE);
        assertThat(created.toString()).doesNotContain(created.clientSecret());

        ArgumentCaptor<MerchantCredential> stored = ArgumentCaptor.forClass(MerchantCredential.class);
        verify(credentialRepository).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getClientSecretHash()).isEqualTo(CredentialSecrets.sha256Hex(created.clientSecret()));
        assertThat(stored.getValue().getClientSecretHash()).doesNotContain(created.clientSecret());
        assertThat(stored.getValue().getMerchantId()).isEqualTo(merchant.getId());
        verify(merchantLockRepository).findByIdForUpdate(merchant.getId());
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-023: creating is audited with ids only (no secret, hash or label)")
    void createIsAudited() {
        service.create(userId, "production");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditEvents).record(eq(userId), eq(ActorRole.MERCHANT), eq("CREDENTIAL_CREATED"), eq("CREDENTIAL"),
                any(), details.capture());
        assertThat(details.getValue()).containsOnlyKeys("merchantId");
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-018: a third ACTIVE credential gives 409 CREDENTIAL_LIMIT_REACHED and stores nothing")
    void limitOfTwoActive() {
        when(credentialRepository.countByMerchantIdAndStatus(merchant.getId(), CredentialStatus.ACTIVE)).thenReturn(2L);

        assertThatThrownBy(() -> service.create(userId, "third")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CREDENTIAL_LIMIT_REACHED));
        verify(credentialRepository, never()).saveAndFlush(any());
        verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-018: revoking an own credential marks it REVOKED and audits it")
    void revokeOwn() {
        MerchantCredential credential = withId(new MerchantCredential(merchant.getId(), "sdc_x", "h".repeat(64), "prod"));
        UUID id = UUID.randomUUID();
        when(credentialRepository.findByIdAndMerchantId(id, merchant.getId())).thenReturn(Optional.of(credential));

        service.revoke(userId, id);

        assertThat(credential.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(credential.getRevokedAt()).isNotNull();
        verify(credentialRepository).save(credential);
        verify(auditEvents).record(eq(userId), eq(ActorRole.MERCHANT), eq("CREDENTIAL_REVOKED"), eq("CREDENTIAL"),
                any(), any());
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-018: revoking twice is a no-op (no second audit, revokedAt unchanged)")
    void revokeTwice() {
        MerchantCredential credential = new MerchantCredential(merchant.getId(), "sdc_x", "h".repeat(64), "prod");
        credential.revoke(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        UUID id = UUID.randomUUID();
        when(credentialRepository.findByIdAndMerchantId(id, merchant.getId())).thenReturn(Optional.of(credential));

        service.revoke(userId, id);

        assertThat(credential.getRevokedAt()).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        verify(credentialRepository, never()).save(any());
        verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TST-032 BR-25: another merchant's or an unknown credential gives 404 NOT_FOUND")
    void revokeForeignOrUnknown() {
        UUID id = UUID.randomUUID();
        when(credentialRepository.findByIdAndMerchantId(id, merchant.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(userId, id)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
    }

    /** JPA assigns the id on save; the mocked repository does it here. */
    private static MerchantCredential withId(MerchantCredential credential) {
        org.springframework.test.util.ReflectionTestUtils.setField(credential, "id", UUID.randomUUID());
        return credential;
    }

    @Test
    @DisplayName("TST-029 BR-26: list validates page and size")
    void listValidation() {
        for (int[] bad : new int[][] {{-1, 20}, {0, 0}, {0, 101}}) {
            assertThatThrownBy(() -> service.list(userId, bad[0], bad[1])).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-018: a merchant user without a merchant record gets 404")
    void noMerchant() {
        UUID other = UUID.randomUUID();
        when(merchantRepository.findByUserId(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(other, "x")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
