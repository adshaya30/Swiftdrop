package com.swiftdrop.auth.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.login.service.JwtService;
import com.swiftdrop.auth.merchant.entity.MerchantCredential;
import com.swiftdrop.auth.merchant.repository.MerchantCredentialRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("TST-032")
class OAuthTokenServiceTest {

    static final String CLIENT_ID = "sdc_AAAAAAAAAAAAAAAAAAAAAAAA";
    static final String SECRET = CredentialSecrets.newClientSecret();

    @Mock MerchantCredentialRepository credentialRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;

    OAuthTokenService service;
    Merchant merchant;
    User user;
    MerchantCredential credential;

    @BeforeEach
    void setUp() {
        service = new OAuthTokenService(credentialRepository, merchantRepository, userRepository, jwtService);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        merchant = new Merchant(user.getId(), "Lanka Crafts", MerchantStatus.APPROVED);
        merchant.setId(UUID.randomUUID());
        credential = new MerchantCredential(merchant.getId(), CLIENT_ID, CredentialSecrets.sha256Hex(SECRET), "prod");
        when(credentialRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(credential));
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.issueClientToken(any(), any(), any())).thenReturn("jwt-value");
        when(jwtService.getAccessTokenExpirySeconds()).thenReturn(900L);
    }

    private static void assertOAuth(Runnable call, String error, int status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(OAuthException.class, e -> {
            assertThat(e.error()).isEqualTo(error);
            assertThat(e.status()).isEqualTo(status);
        });
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: success returns Bearer, 900 s, both scopes by default and updates last_used_at")
    void success() {
        var response = service.issue("client_credentials", CLIENT_ID, SECRET, null);

        assertThat(response.accessToken()).isEqualTo("jwt-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.scope()).isEqualTo("orders:read orders:write");
        assertThat(credential.getLastUsedAt()).isNotNull();
        verify(credentialRepository).save(credential);
        // sub is the merchant's USER id, mid the merchant id (passed to the shared JwtService)
        verify(jwtService).issueClientToken(merchant, user.getId(), new LinkedHashSet<>(List.of("orders:read", "orders:write")));
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: a requested subset of scopes is honoured, in canonical order")
    void scopeSubset() {
        assertThat(service.issue("client_credentials", CLIENT_ID, SECRET, "orders:read").scope()).isEqualTo("orders:read");
        assertThat(service.issue("client_credentials", CLIENT_ID, SECRET, "orders:write orders:read").scope())
                .isEqualTo("orders:read orders:write");
        assertThat(service.issue("client_credentials", CLIENT_ID, SECRET, "  orders:write  ").scope())
                .isEqualTo("orders:write");
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "orders:read admin", "orders:delete", "ORDERS:READ"})
    @DisplayName("TST-032 FR-AUTH-019: any scope outside orders:read and orders:write gives 400 invalid_scope")
    void invalidScope(String scope) {
        assertOAuth(() -> service.issue("client_credentials", CLIENT_ID, SECRET, scope), "invalid_scope", 400);
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: missing grant_type is invalid_request, another grant is unsupported_grant_type")
    void grantType() {
        assertOAuth(() -> service.issue(null, CLIENT_ID, SECRET, null), "invalid_request", 400);
        assertOAuth(() -> service.issue(" ", CLIENT_ID, SECRET, null), "invalid_request", 400);
        assertOAuth(() -> service.issue("password", CLIENT_ID, SECRET, null), "unsupported_grant_type", 400);
        assertOAuth(() -> service.issue("authorization_code", CLIENT_ID, SECRET, null), "unsupported_grant_type", 400);
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: missing client_id or client_secret is invalid_request")
    void missingClientParameters() {
        assertOAuth(() -> service.issue("client_credentials", null, SECRET, null), "invalid_request", 400);
        assertOAuth(() -> service.issue("client_credentials", CLIENT_ID, null, null), "invalid_request", 400);
        assertOAuth(() -> service.issue("client_credentials", "", "", null), "invalid_request", 400);
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: wrong secret and unknown client both give 401 invalid_client")
    void wrongSecretAndUnknownClient() {
        assertOAuth(() -> service.issue("client_credentials", CLIENT_ID, SECRET + "x", null), "invalid_client", 401);
        assertOAuth(() -> service.issue("client_credentials", "sdc_unknown", SECRET, null), "invalid_client", 401);
        verify(jwtService, never()).issueClientToken(any(), any(), any());
        assertThat(credential.getLastUsedAt()).isNull();
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: a revoked credential gives invalid_client")
    void revokedCredential() {
        credential.revoke(java.time.Instant.now());
        assertOAuth(() -> service.issue("client_credentials", CLIENT_ID, SECRET, null), "invalid_client", 401);
    }

    @ParameterizedTest
    @EnumSource(value = MerchantStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    @DisplayName("TST-032 BR-12: a merchant that is not APPROVED gives invalid_client")
    void merchantNotApproved(MerchantStatus status) {
        merchant.setStatus(status);
        assertOAuth(() -> service.issue("client_credentials", CLIENT_ID, SECRET, null), "invalid_client", 401);
        verify(jwtService, never()).issueClientToken(any(), any(), any());
    }

    @Test
    @DisplayName("TST-032 BR-23: a DEACTIVATED merchant user gives invalid_client")
    void deactivatedUser() {
        user.setStatus(UserStatus.DEACTIVATED);
        assertOAuth(() -> service.issue("client_credentials", CLIENT_ID, SECRET, null), "invalid_client", 401);
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: client authentication takes precedence over scope errors")
    void authenticationBeforeScope() {
        assertOAuth(() -> service.issue("client_credentials", CLIENT_ID, "wrong", "admin"), "invalid_client", 401);
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: parseScopes keeps a set in canonical order")
    void parseScopesDirect() {
        Set<String> scopes = OAuthTokenService.parseScopes("orders:write orders:read orders:write");
        assertThat(scopes).containsExactly("orders:read", "orders:write");
    }
}
