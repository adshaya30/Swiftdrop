package com.swiftdrop.auth.merchant.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.login.service.JwtService;
import com.swiftdrop.auth.merchant.dto.OAuthTokenResponse;
import com.swiftdrop.auth.merchant.entity.MerchantCredential;
import com.swiftdrop.auth.merchant.repository.MerchantCredentialRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client-credentials grant. SRS v2.1 - FR-AUTH-019, BR-12 (only APPROVED merchants with an ACTIVE user get tokens),
 * BR-23. A wrong or unknown client, a wrong secret, a revoked credential, a merchant that is not APPROVED and a user
 * that is not ACTIVE all give the same {@code invalid_client}.
 */
@Service
public class OAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";
    /** Canonical scope order of the response. */
    static final List<String> SUPPORTED_SCOPES = List.of("orders:read", "orders:write");
    /** Compared against when the client id is unknown, so that unknown and wrong-secret take the same path. */
    private static final String DUMMY_HASH = CredentialSecrets.sha256Hex("swiftdrop-unknown-client-dummy-secret");

    private final MerchantCredentialRepository credentialRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public OAuthTokenService(MerchantCredentialRepository credentialRepository, MerchantRepository merchantRepository,
                             UserRepository userRepository, JwtService jwtService) {
        this.credentialRepository = credentialRepository;
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public OAuthTokenResponse issue(String grantType, String clientId, String clientSecret, String scope) {
        if (grantType == null || grantType.isBlank()) {
            throw OAuthException.invalidRequest("grant_type is required");
        }
        if (!GRANT_CLIENT_CREDENTIALS.equals(grantType)) {
            throw OAuthException.unsupportedGrantType();
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw OAuthException.invalidRequest("client_id and client_secret are required");
        }

        MerchantCredential credential = authenticate(clientId, clientSecret);
        Set<String> scopes = parseScopes(scope);

        Merchant merchant = merchantRepository.findById(credential.getMerchantId())
                .orElseThrow(OAuthException::invalidClient);
        User user = userRepository.findById(merchant.getUserId()).orElseThrow(OAuthException::invalidClient);

        credential.markUsed(Instant.now());
        credentialRepository.save(credential);
        String token = jwtService.issueClientToken(merchant, user.getId(), scopes);
        log.info("Client token issued: merchantId={}", merchant.getId());
        return new OAuthTokenResponse(token, "Bearer", jwtService.getAccessTokenExpirySeconds(),
                String.join(" ", scopes));
    }

    /** Verifies the secret in constant time and the state of credential, merchant and user (BR-12, BR-23). */
    private MerchantCredential authenticate(String clientId, String clientSecret) {
        MerchantCredential credential = credentialRepository.findByClientId(clientId).orElse(null);
        String expected = credential != null ? credential.getClientSecretHash() : DUMMY_HASH;
        boolean secretMatches = CredentialSecrets.constantTimeEquals(CredentialSecrets.sha256Hex(clientSecret), expected);

        boolean usable = false;
        if (credential != null && secretMatches && credential.isActive()) {
            Merchant merchant = merchantRepository.findById(credential.getMerchantId()).orElse(null);
            User user = merchant == null ? null : userRepository.findById(merchant.getUserId()).orElse(null);
            usable = merchant != null && user != null && merchant.getStatus() == MerchantStatus.APPROVED
                    && user.getStatus() == UserStatus.ACTIVE;
        }
        if (!usable) {
            log.warn("Client authentication failed");
            throw OAuthException.invalidClient();
        }
        return credential;
    }

    /** Default is both scopes; otherwise a subset of orders:read and orders:write, in canonical order. */
    static Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return new LinkedHashSet<>(SUPPORTED_SCOPES);
        }
        Set<String> requested = new LinkedHashSet<>(List.of(scope.trim().split("\\s+")));
        if (!SUPPORTED_SCOPES.containsAll(requested)) {
            throw OAuthException.invalidScope();
        }
        Set<String> ordered = new LinkedHashSet<>();
        SUPPORTED_SCOPES.stream().filter(requested::contains).forEach(ordered::add);
        return ordered;
    }
}
