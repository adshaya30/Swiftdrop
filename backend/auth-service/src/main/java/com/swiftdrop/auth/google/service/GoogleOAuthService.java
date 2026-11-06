package com.swiftdrop.auth.google.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.google.domain.OAuthLoginState;
import com.swiftdrop.auth.google.domain.OAuthLoginStateRepository;
import com.swiftdrop.auth.google.dto.GoogleLoginInitResult;
import com.swiftdrop.auth.google.dto.GoogleUserClaims;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Service orchestrating Google OAuth 2.0 PKCE flow and account management.
 * SRS v2.1 – Flow 4.5, FR-AUTH-009, FR-AUTH-010, FR-AUTH-011
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private static final String DEFAULT_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String DEFAULT_TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String publicBaseUrl;
    private final String authUrl;
    private final String tokenUrl;
    private final boolean cookieSecure;

    private final OAuthLoginStateRepository oAuthLoginStateRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final GoogleOidcVerifier googleOidcVerifier;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public GoogleOAuthService(
            @Value("${auth.google.client-id:${GOOGLE_CLIENT_ID:}}") String clientId,
            @Value("${auth.google.client-secret:${GOOGLE_CLIENT_SECRET:}}") String clientSecret,
            @Value("${auth.google.redirect-uri:${GOOGLE_REDIRECT_URI:http://localhost:8080/api/v1/auth/google/callback}}") String redirectUri,
            @Value("${swiftdrop.public-base-url:${PUBLIC_BASE_URL:http://localhost:5173}}") String publicBaseUrl,
            @Value("${auth.google.auth-url:" + DEFAULT_AUTH_URL + "}") String authUrl,
            @Value("${auth.google.token-url:" + DEFAULT_TOKEN_URL + "}") String tokenUrl,
            @Value("${auth.cookie.secure:${COOKIE_SECURE:false}}") boolean cookieSecure,
            OAuthLoginStateRepository oAuthLoginStateRepository,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            GoogleOidcVerifier googleOidcVerifier,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.publicBaseUrl = publicBaseUrl != null ? publicBaseUrl.replaceAll("/+$", "") : "http://localhost:5173";
        this.authUrl = authUrl;
        this.tokenUrl = tokenUrl;
        this.cookieSecure = cookieSecure;
        this.oAuthLoginStateRepository = oAuthLoginStateRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.googleOidcVerifier = googleOidcVerifier;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    /**
     * FR-AUTH-011: Google login is enabled only if both client ID and client secret are set.
     */
    public boolean isGoogleEnabled() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    /**
     * Initiates Google OAuth 2.0 PKCE flow (Flow 4.5).
     * Generates state, nonce, and code_verifier, persisting SHA-256 state hash in database.
     */
    @Transactional
    public GoogleLoginInitResult initiateLogin() {
        if (!isGoogleEnabled()) {
            throw new IllegalStateException("Google OAuth is disabled by configuration");
        }

        // 1. Generate state, nonce, and PKCE code_verifier
        String rawState = generateRandomBase64Url(32);
        String nonce = generateRandomBase64Url(32);
        String codeVerifier = generateRandomBase64Url(32);
        String codeChallenge = generateS256CodeChallenge(codeVerifier);

        // 2. Compute SHA-256 of state for persistent storage
        String stateHash = sha256Hex(rawState);
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        OAuthLoginState loginState = new OAuthLoginState(stateHash, nonce, codeVerifier, expiresAt);
        oAuthLoginStateRepository.save(loginState);
        log.debug("Initiated Google OAuth login: stateHash={} (expires in 5m)", stateHash);

        // 3. Build Google authorization URL
        String authorizationUrl = UriComponentsBuilder.fromHttpUrl(authUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid email profile")
                .queryParam("state", rawState)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .toUriString();

        return new GoogleLoginInitResult(authorizationUrl, rawState);
    }

    /**
     * Exchanges authorization code, validates ID token, enforces account rules, and creates session.
     * Returns a pair of: [Redirect URI, optional Set-Cookie header for sd_refresh].
     */
    @Transactional
    public CallbackOutcome handleCallback(String code, String state, String cookieState) {
        // 1. State validation against cookie
        if (state == null || state.isBlank() || cookieState == null || !state.equals(cookieState)) {
            log.warn("OAuth state mismatch or missing cookie: state={}, cookieState={}", state, cookieState);
            return CallbackOutcome.failure(publicBaseUrl + "/login?error=google_failed");
        }

        // 2. State hash lookup in database
        String stateHash = sha256Hex(state);
        Optional<OAuthLoginState> stateOpt = oAuthLoginStateRepository.findByStateHash(stateHash);
        if (stateOpt.isEmpty()) {
            log.warn("OAuth state not found in database: stateHash={}", stateHash);
            return CallbackOutcome.failure(publicBaseUrl + "/login?error=google_failed");
        }

        OAuthLoginState loginState = stateOpt.get();
        if (loginState.getExpiresAt().isBefore(Instant.now())) {
            log.warn("OAuth state expired: stateHash={}", stateHash);
            oAuthLoginStateRepository.delete(loginState);
            return CallbackOutcome.failure(publicBaseUrl + "/login?error=google_failed");
        }

        // Delete immediately after single use to prevent replay
        oAuthLoginStateRepository.delete(loginState);

        // 3. Server-side code exchange with Google using RestClient
        String idToken;
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("code", code);
            body.add("code_verifier", loginState.getCodeVerifier());
            body.add("grant_type", "authorization_code");
            body.add("redirect_uri", redirectUri);

            String responseBody = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode tokenJson = objectMapper.readTree(responseBody);
            if (!tokenJson.has("id_token")) {
                log.error("Google token response did not contain id_token");
                return CallbackOutcome.failure(publicBaseUrl + "/login?error=google_failed");
            }
            idToken = tokenJson.get("id_token").asText();
        } catch (Exception e) {
            log.error("Google token exchange failed: {}", e.getMessage());
            return CallbackOutcome.failure(publicBaseUrl + "/login?error=google_failed");
        }

        // 4. Validate ID token claims
        GoogleUserClaims claims;
        try {
            claims = googleOidcVerifier.verifyIdToken(idToken, loginState.getNonce());
        } catch (Exception e) {
            log.warn("Google ID token validation failed: {}", e.getMessage());
            return CallbackOutcome.failure(publicBaseUrl + "/login?error=google_failed");
        }

        if (!claims.emailVerified()) {
            log.warn("Google email is not verified for sub={}", claims.sub());
            return CallbackOutcome.failure(publicBaseUrl + "/login?error=google_failed");
        }

        // 5. Account resolution per FR-AUTH-010
        String email = claims.email().trim().toLowerCase();
        Optional<User> byGoogleSub = userRepository.findByGoogleSub(claims.sub());

        User authenticatedUser;
        if (byGoogleSub.isPresent()) {
            authenticatedUser = byGoogleSub.get();
            if (authenticatedUser.getStatus() == UserStatus.DEACTIVATED) {
                log.warn("Deactivated Google user attempted login: userId={}", authenticatedUser.getId());
                return CallbackOutcome.failure(publicBaseUrl + "/login?error=account_deactivated");
            }
            authenticatedUser.setLastLoginAt(Instant.now());
            userRepository.save(authenticatedUser);
        } else {
            // Check for existing local email conflict -> NEVER MERGE!
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                log.warn("Conflict: Account with email {} already exists with authProvider={}. Rejecting merge.",
                        email, byEmail.get().getAuthProvider());
                return CallbackOutcome.failure(publicBaseUrl + "/login?error=account_exists");
            }

            // Create new CUSTOMER account
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFullName(claims.name() != null && !claims.name().isBlank() ? claims.name().trim() : "Google User");
            newUser.setAuthProvider(AuthProvider.GOOGLE);
            newUser.setRole(UserRole.CUSTOMER);
            newUser.setStatus(UserStatus.ACTIVE);
            newUser.setGoogleSub(claims.sub());
            newUser.setPasswordHash(null);
            newUser.setPhone(null);
            newUser.setMustChangePassword(false);
            newUser.setFailedLoginCount(0);
            newUser.setLastLoginAt(Instant.now());

            authenticatedUser = userRepository.save(newUser);
            log.info("Registered new Google CUSTOMER: userId={}, email={}", authenticatedUser.getId(), email);
        }

        // 6. Issue refresh token and session cookie
        String rawRefreshToken = refreshTokenService.createRefreshToken(authenticatedUser.getId());
        ResponseCookie refreshCookie = ResponseCookie.from("sd_refresh", rawRefreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(7 * 24 * 3600)
                .build();

        return CallbackOutcome.success(publicBaseUrl + "/auth/callback", refreshCookie);
    }

    private String generateRandomBase64Url(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateS256CodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    public record CallbackOutcome(String redirectUrl, ResponseCookie refreshCookie) {
        public static CallbackOutcome success(String redirectUrl, ResponseCookie cookie) {
            return new CallbackOutcome(redirectUrl, cookie);
        }
        public static CallbackOutcome failure(String redirectUrl) {
            return new CallbackOutcome(redirectUrl, null);
        }
    }
}
