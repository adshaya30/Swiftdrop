package com.swiftdrop.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenService RS256 Unit Tests (TST-013)")
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService("classpath:certs/test_private_key.pem", objectMapper);
    }

    @Test
    @DisplayName("RS256 JWT header has alg=RS256, typ=JWT, kid=swiftdrop-1")
    void tokenHeader_hasCorrectAttributes() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.issueAccessToken(userId, "CUSTOMER", false, null);

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = objectMapper.readValue(headerBytes, Map.class);

        assertThat(header.get("alg")).isEqualTo("RS256");
        assertThat(header.get("typ")).isEqualTo("JWT");
        assertThat(header.get("kid")).isEqualTo("swiftdrop-1");
    }

    @Test
    @DisplayName("RS256 JWT claims adhere to Section 3.6 contract")
    void tokenClaims_containMandatorySRSClaims() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.issueAccessToken(userId, "CUSTOMER", false, null);

        Map<String, Object> claims = jwtTokenService.parseAndVerifyToken(token);

        assertThat(claims.get("sub")).isEqualTo(userId.toString());
        assertThat(claims.get("role")).isEqualTo("CUSTOMER");
        assertThat(claims.get("typ")).isEqualTo("user");
        assertThat(claims.get("iss")).isEqualTo("swiftdrop");
        assertThat(claims.get("aud")).isEqualTo("swiftdrop-api");
        assertThat(claims.get("jti")).isNotNull();

        Number iat = (Number) claims.get("iat");
        Number exp = (Number) claims.get("exp");
        assertThat(exp.longValue() - iat.longValue()).isEqualTo(900L); // 15 minutes
    }

    @Test
    @DisplayName("No sensitive data in JWT access token")
    void tokenClaims_containNoSensitiveData() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.issueAccessToken(userId, "CUSTOMER", false, null);

        Map<String, Object> claims = jwtTokenService.parseAndVerifyToken(token);

        assertThat(claims.containsKey("email")).isFalse();
        assertThat(claims.containsKey("phone")).isFalse();
        assertThat(claims.containsKey("fullName")).isFalse();
        assertThat(claims.containsKey("password")).isFalse();
        assertThat(claims.containsKey("passwordHash")).isFalse();
    }

    @Test
    @DisplayName("Conditional pwc claim present when mustChangePassword is true")
    void conditionalClaim_pwc_whenMustChangePassword() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.issueAccessToken(userId, "COURIER", true, null);

        Map<String, Object> claims = jwtTokenService.parseAndVerifyToken(token);
        assertThat(claims.get("pwc")).isEqualTo(true);
    }

    @Test
    @DisplayName("Conditional mid claim present when merchantId is provided")
    void conditionalClaim_mid_whenMerchantIdPresent() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        String token = jwtTokenService.issueAccessToken(userId, "MERCHANT", false, merchantId);

        Map<String, Object> claims = jwtTokenService.parseAndVerifyToken(token);
        assertThat(claims.get("mid")).isEqualTo(merchantId.toString());
    }

    @Test
    @DisplayName("Fail-fast when private key configuration is missing (no ephemeral fallback)")
    void blankPath_throwsIllegalStateException() {
        assertThatThrownBy(() -> new JwtTokenService("", objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");

        assertThatThrownBy(() -> new JwtTokenService(null, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("Reject invalid non-existent private key path")
    void rejectInvalidPrivateKeyPath() {
        assertThatThrownBy(() -> new JwtTokenService("/non/existent/path/to/key.pem", objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("TST-013: Tampered signature rejection")
    void verifyToken_rejectsTamperedSignature() {
        UUID userId = UUID.randomUUID();
        String validToken = jwtTokenService.issueAccessToken(userId, "CUSTOMER", false, null);
        String[] parts = validToken.split("\\.");

        // Tamper with the signature portion
        String tamperedToken = parts[0] + "." + parts[1] + ".tamperedSignature";

        assertThatThrownBy(() -> jwtTokenService.parseAndVerifyToken(tamperedToken))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("TST-013: Tampered payload rejection")
    void verifyToken_rejectsTamperedPayload() {
        UUID userId = UUID.randomUUID();
        String validToken = jwtTokenService.issueAccessToken(userId, "CUSTOMER", false, null);
        String[] parts = validToken.split("\\.");

        // Replace claims payload with role=ADMIN
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"forged\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtTokenService.parseAndVerifyToken(tamperedToken))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("TST-013: Token signed with a different key is rejected")
    void verifyToken_rejectsTokenSignedWithDifferentKey() throws Exception {
        // Generate a foreign RSA key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair foreignPair = kpg.generateKeyPair();

        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"swiftdrop-1\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"fake-user\",\"role\":\"CUSTOMER\"}".getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(foreignPair.getPrivate());
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());

        String foreignToken = signingInput + "." + signature;

        assertThatThrownBy(() -> jwtTokenService.parseAndVerifyToken(foreignToken))
                .isInstanceOf(SecurityException.class);
    }
}
