package com.swiftdrop.auth.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.auth.google.dto.GoogleUserClaims;
import com.swiftdrop.auth.google.service.DefaultGoogleOidcVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DefaultGoogleOidcVerifier (FR-AUTH-009, Flow 4.5).
 */
@DisplayName("DefaultGoogleOidcVerifier Unit Tests")
class DefaultGoogleOidcVerifierTest {

    private RSAKey rsaKey;
    private RSASSASigner signer;
    private DefaultGoogleOidcVerifier verifier;
    private final String expectedClientId = "test-client-id-xyz";

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        rsaKey = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                .privateKey((RSAPrivateKey) kp.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        signer = new RSASSASigner(rsaKey);

        JWKSet jwkSet = new JWKSet(rsaKey);
        ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);
        verifier = new DefaultGoogleOidcVerifier(expectedClientId, jwkSource);
    }

    private String createToken(String issuer, String audience, String sub, String email, Boolean emailVerified,
                               String nonce, Date expirationTime) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(sub)
                .claim("email", email)
                .claim("name", "Test Google User")
                .expirationTime(expirationTime);

        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }
        if (nonce != null) {
            builder.claim("nonce", nonce);
        }

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                builder.build()
        );
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("verifyIdToken successfully extracts claims for valid Google ID token")
    void verifyIdToken_validToken_success() throws Exception {
        String token = createToken(
                "https://accounts.google.com",
                expectedClientId,
                "google-sub-12345",
                "user@example.com",
                true,
                "expected-nonce-999",
                new Date(System.currentTimeMillis() + 60000)
        );

        GoogleUserClaims claims = verifier.verifyIdToken(token, "expected-nonce-999");

        assertThat(claims).isNotNull();
        assertThat(claims.sub()).isEqualTo("google-sub-12345");
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.name()).isEqualTo("Test Google User");
        assertThat(claims.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("verifyIdToken throws SecurityException on invalid issuer")
    void verifyIdToken_invalidIssuer_throwsException() throws Exception {
        String token = createToken(
                "https://malicious-issuer.com",
                expectedClientId,
                "google-sub-12345",
                "user@example.com",
                true,
                "nonce-123",
                new Date(System.currentTimeMillis() + 60000)
        );

        assertThatThrownBy(() -> verifier.verifyIdToken(token, "nonce-123"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    @DisplayName("verifyIdToken throws SecurityException on audience mismatch")
    void verifyIdToken_audienceMismatch_throwsException() throws Exception {
        String token = createToken(
                "https://accounts.google.com",
                "wrong-client-id",
                "google-sub-12345",
                "user@example.com",
                true,
                "nonce-123",
                new Date(System.currentTimeMillis() + 60000)
        );

        assertThatThrownBy(() -> verifier.verifyIdToken(token, "nonce-123"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Audience");
    }

    @Test
    @DisplayName("verifyIdToken throws SecurityException on expired token")
    void verifyIdToken_expiredToken_throwsException() throws Exception {
        String token = createToken(
                "https://accounts.google.com",
                expectedClientId,
                "google-sub-12345",
                "user@example.com",
                true,
                "nonce-123",
                new Date(System.currentTimeMillis() - 60000)
        );

        assertThatThrownBy(() -> verifier.verifyIdToken(token, "nonce-123"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Expired");
    }

    @Test
    @DisplayName("verifyIdToken throws SecurityException on nonce mismatch (replay protection)")
    void verifyIdToken_nonceMismatch_throwsException() throws Exception {
        String token = createToken(
                "https://accounts.google.com",
                expectedClientId,
                "google-sub-12345",
                "user@example.com",
                true,
                "different-nonce",
                new Date(System.currentTimeMillis() + 60000)
        );

        assertThatThrownBy(() -> verifier.verifyIdToken(token, "expected-nonce"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Nonce");
    }

    @Test
    @DisplayName("verifyIdToken throws SecurityException when email is not verified by Google")
    void verifyIdToken_unverifiedEmail_throwsException() throws Exception {
        String token = createToken(
                "https://accounts.google.com",
                expectedClientId,
                "google-sub-12345",
                "unverified@example.com",
                false,
                "nonce-123",
                new Date(System.currentTimeMillis() + 60000)
        );

        assertThatThrownBy(() -> verifier.verifyIdToken(token, "nonce-123"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("verified");
    }
}
