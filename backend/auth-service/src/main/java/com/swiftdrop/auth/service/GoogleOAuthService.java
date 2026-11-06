package com.swiftdrop.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.dto.response.GoogleCallbackResult;
import com.swiftdrop.auth.dto.response.GoogleLoginInitResult;
import com.swiftdrop.auth.dto.response.GoogleUserClaims;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.repository.OAuthLoginStateRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Service orchestrating Google OAuth 2.0 PKCE flow and account management.
 * SRS v2.1 – Flow 4.5, FR-AUTH-009, FR-AUTH-010, FR-AUTH-011
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendUrl;

    private final OAuthLoginStateRepository oAuthLoginStateRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoogleOidcVerifier googleOidcVerifier;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public GoogleOAuthService(
            @Value("${auth.google.client-id:${GOOGLE_CLIENT_ID:}}") String clientId,
            @Value("${auth.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") String clientSecret,
            @Value("${auth.google.redirect-uri:${GOOGLE_REDIRECT_URI:http://localhost:8080/api/v1/auth/google/callback}}") String redirectUri,
            @Value("${auth.frontend-url:${FRONTEND_URL:http://localhost:5173}}") String frontendUrl,
            OAuthLoginStateRepository oAuthLoginStateRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            GoogleOidcVerifier googleOidcVerifier,
            ObjectMapper objectMapper,
            Clock clock) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.oAuthLoginStateRepository = oAuthLoginStateRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.googleOidcVerifier = googleOidcVerifier;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.restTemplate = new RestTemplate();
    }

    /**
     * FR-AUTH-011: Google login is enabled only if both client ID and client secret are set.
     */
    public boolean isGoogleEnabled() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /**
     * Initiates Google OAuth 2.0 PKCE flow (Flow 4.5).
     * Generates state, nonce, and code_verifier, persisting state_hash in database.
     */
    @Transactional
    public GoogleLoginInitResult initiateLogin() {
        if (!isGoogleEnabled()) {
            throw new IllegalStateException("Google OAuth is disabled by configuration");
        }

        // 1. Generate cryptographic random tokens
        String state = generateRandomBase64Url(32);
        String nonce = generateRandomBase64Url(32);
        String codeVerifier = generateRandomBase64Url(64);
        String codeChallenge = deriveCodeChallenge(codeVerifier);

        // 2. Hash state and persist to database (expires in 5 minutes)
        String stateHash = sha256Hex(state);
        Instant expiresAt = clock.instant().plus(5, ChronoUnit.MINUTES);
        OAuthLoginState stateEntity = new OAuthLoginState(stateHash, nonce, codeVerifier, expiresAt);
        oAuthLoginStateRepository.save(stateEntity);

        // 3. Build Google authorization URL with PKCE and OIDC parameters
        String authorizationUrl = UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_ENDPOINT)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();

        return new GoogleLoginInitResult(authorizationUrl, state);
    }

    /**
     * Handles Google OAuth callback (Flow 4.5, FR-AUTH-010).
     */
    @Transactional
    public GoogleCallbackResult handleCallback(
            String code,
            String state,
            String cookieState,
            String error,
            String userAgent,
            String ip) {

        // 1. Check for provider denial or error
        if (error != null && !error.isBlank()) {
            if ("access_denied".equalsIgnoreCase(error)) {
                return new GoogleCallbackResult(frontendUrl + "/login?error=google_denied", null);
            }
            log.warn("Google OAuth callback received error: {}", error);
            return new GoogleCallbackResult(frontendUrl + "/login?error=google_failed", null);
        }

        // 2. Validate state cookie against query state
        if (state == null || state.isBlank() || cookieState == null || !state.equals(cookieState)) {
            log.warn("OAuth state validation failed: state parameter does not match sd_oauth cookie");
            return new GoogleCallbackResult(frontendUrl + "/login?error=google_failed", null);
        }

        // 3. Look up state in database and verify expiration
        String stateHash = sha256Hex(state);
        Optional<OAuthLoginState> stateOpt = oAuthLoginStateRepository.findByStateHash(stateHash);
        if (stateOpt.isEmpty()) {
            log.warn("OAuth state record not found (possible replay attack)");
            return new GoogleCallbackResult(frontendUrl + "/login?error=google_failed", null);
        }

        OAuthLoginState loginState = stateOpt.get();
        if (loginState.getExpiresAt().isBefore(clock.instant())) {
            log.warn("OAuth state has expired");
            oAuthLoginStateRepository.deleteByStateHash(stateHash);
            return new GoogleCallbackResult(frontendUrl + "/login?error=google_failed", null);
        }

        // 4. Atomically consume one-time state
        oAuthLoginStateRepository.deleteByStateHash(stateHash);

        if (code == null || code.isBlank()) {
            log.warn("Missing authorization code in Google callback");
            return new GoogleCallbackResult(frontendUrl + "/login?error=google_failed", null);
        }

        // 5. Server-side token exchange (exchanging code + code_verifier)
        String idTokenString;
        try {
            idTokenString = exchangeCodeForIdToken(code, loginState.getCodeVerifier());
        } catch (Exception e) {
            log.warn("Failed to exchange authorization code with Google: {}", e.getMessage());
            return new GoogleCallbackResult(frontendUrl + "/login?error=google_failed", null);
        }

        // 6. Cryptographically verify ID token and nonce
        GoogleUserClaims claims;
        try {
            claims = googleOidcVerifier.verifyIdToken(idTokenString, loginState.getNonce());
        } catch (Exception e) {
            log.warn("Google ID token verification failed: {}", e.getMessage());
            return new GoogleCallbackResult(frontendUrl + "/login?error=google_failed", null);
        }

        // 7. Apply Google Account Rules (FR-AUTH-010)
        String googleSub = claims.sub();
        String email = claims.email().toLowerCase().trim();
        String fullName = claims.name();

        User user;
        Optional<User> userBySub = userRepository.findByGoogleSub(googleSub);
        if (userBySub.isPresent()) {
            user = userBySub.get();
            if (user.getStatus() == UserStatus.DEACTIVATED) {
                log.warn("Google login rejected: user {} is DEACTIVATED", user.getId());
                return new GoogleCallbackResult(frontendUrl + "/login?error=account_deactivated", null);
            }
        } else {
            // Check if email already belongs to a LOCAL account
            Optional<User> userByEmail = userRepository.findByEmailIgnoreCase(email);
            if (userByEmail.isPresent()) {
                log.warn("Google login conflict: account already registered with LOCAL credentials for user {}", userByEmail.get().getId());
                return new GoogleCallbackResult(frontendUrl + "/login?error=account_exists", null);
            }

            // New Google user registration as CUSTOMER
            user = new User();
            user.setFullName(fullName != null && !fullName.isBlank() ? fullName : "Customer");
            user.setEmail(email);
            user.setPhone(null);
            user.setPasswordHash(null);
            user.setRole(UserRole.CUSTOMER);
            user.setAuthProvider(AuthProvider.GOOGLE);
            user.setStatus(UserStatus.ACTIVE);
            user.setGoogleSub(googleSub);
            user.setMustChangePassword(false);
            user.setFailedLoginCount(0);
            user = userRepository.saveAndFlush(user);
            log.info("New customer registered via Google OAuth userId={}", user.getId());
        }

        // 8. Issue Refresh Token session (FR-AUTH-007, Phase 7)
        String rawRefreshToken = generateRandomBase64Url(32);
        String tokenHash = sha256Hex(rawRefreshToken);
        Instant now = clock.instant();

        UUID familyId = UUID.randomUUID();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setFamilyId(familyId);
        refreshToken.setFamilyCreatedAt(now);
        refreshToken.setIssuedAt(now);
        refreshToken.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
        refreshToken.setCreatedAt(now);
        refreshToken.setUserAgent(userAgent);
        refreshToken.setIpAddress(ip);
        refreshTokenRepository.saveAndFlush(refreshToken);

        user.setLastLoginAt(now);
        user.setFailedLoginCount(0);
        userRepository.save(user);

        log.info("Google OAuth login successful for userId={}", user.getId());

        // Redirect to frontend callback route with refresh token cookie
        return new GoogleCallbackResult(frontendUrl + "/auth/callback", rawRefreshToken);
    }

    /**
     * Server-side token exchange with Google token endpoint.
     */
    public String exchangeCodeForIdToken(String code, String codeVerifier) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");
        body.add("code_verifier", codeVerifier);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(GOOGLE_TOKEN_ENDPOINT, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Google token exchange returned status " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode idTokenNode = root.get("id_token");
        if (idTokenNode == null || idTokenNode.asText().isBlank()) {
            throw new IllegalStateException("Google token response did not contain id_token");
        }

        return idTokenNode.asText();
    }

    private String generateRandomBase64Url(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String deriveCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
