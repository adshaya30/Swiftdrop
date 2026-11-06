package com.swiftdrop.auth.google.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.swiftdrop.auth.google.dto.GoogleUserClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * Validates Google OIDC ID tokens using Nimbus JOSE+JWT.
 * Enforces:
 * - RS256 signature verification against Google's published JWKS
 * - Issuer validation (https://accounts.google.com or accounts.google.com)
 * - Audience validation (must match configured Google client ID)
 * - Expiration check
 * - Nonce matching (mitigating replay attacks)
 * - email_verified must be true
 *
 * SRS v2.1 – Flow 4.5, FR-AUTH-009, FR-AUTH-010
 */
@Component
public class DefaultGoogleOidcVerifier implements GoogleOidcVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleOidcVerifier.class);
    private static final String DEFAULT_GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER_HTTPS = "https://accounts.google.com";
    private static final String ISSUER_ACCOUNTS = "accounts.google.com";

    private final String clientId;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    @Autowired
    public DefaultGoogleOidcVerifier(
            @Value("${auth.google.client-id:${GOOGLE_CLIENT_ID:}}") String clientId,
            @Value("${auth.google.jwks-url:" + DEFAULT_GOOGLE_JWKS_URL + "}") String jwksUrl,
            @Autowired(required = false) JWKSource<SecurityContext> customJwkSource) {
        this.clientId = clientId;
        this.jwtProcessor = new DefaultJWTProcessor<>();

        try {
            JWKSource<SecurityContext> keySource = (customJwkSource != null)
                    ? customJwkSource
                    : new RemoteJWKSet<>(new URL(jwksUrl));

            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
            this.jwtProcessor.setJWSKeySelector(keySelector);
        } catch (Exception e) {
            log.error("Failed to initialize Google OIDC key source: {}", e.getMessage());
            throw new IllegalStateException("Failed to initialize Google JWKS key source", e);
        }
    }

    @Override
    public GoogleUserClaims verifyIdToken(String idTokenString, String expectedNonce) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new SecurityException("ID token is missing or empty");
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(idTokenString);

            // 1. Cryptographic signature and basic claims verification
            JWTClaimsSet claimsSet = jwtProcessor.process(signedJWT, null);

            // 2. Validate Issuer
            String issuer = claimsSet.getIssuer();
            if (issuer == null || (!issuer.equals(ISSUER_HTTPS) && !issuer.equals(ISSUER_ACCOUNTS))) {
                throw new SecurityException("Invalid token issuer: " + issuer);
            }

            // 3. Validate Audience
            List<String> audience = claimsSet.getAudience();
            if (audience == null || clientId == null || clientId.isBlank() || !audience.contains(clientId)) {
                throw new SecurityException("Audience does not match Google client ID");
            }

            // 4. Validate Expiration
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                throw new SecurityException("Token has expired");
            }

            // 5. Validate Nonce
            String tokenNonce = claimsSet.getStringClaim("nonce");
            if (tokenNonce == null || expectedNonce == null || !tokenNonce.equals(expectedNonce)) {
                throw new SecurityException("ID token nonce does not match original login nonce");
            }

            // 6. Validate email_verified
            Boolean emailVerified = claimsSet.getBooleanClaim("email_verified");
            if (emailVerified == null || !emailVerified) {
                throw new SecurityException("Google email is not verified");
            }

            String sub = claimsSet.getSubject();
            String email = claimsSet.getStringClaim("email");
            String name = claimsSet.getStringClaim("name");
            String picture = claimsSet.getStringClaim("picture");

            return new GoogleUserClaims(sub, email, true, name, picture);
        } catch (SecurityException se) {
            log.warn("Google ID token validation failed: {}", se.getMessage());
            throw se;
        } catch (Exception e) {
            log.warn("Google ID token processing error: {}", e.getMessage());
            throw new SecurityException("Failed to parse and verify Google ID token", e);
        }
    }
}
