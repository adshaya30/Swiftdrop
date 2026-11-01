package com.swiftdrop.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenService RS256 Unit Tests")
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
    @DisplayName("Blank path generates ephemeral RSA key pair in development")
    void blankPath_generatesEphemeralKey() {
        JwtTokenService ephemeralService = new JwtTokenService("", objectMapper);
        UUID userId = UUID.randomUUID();
        String token = ephemeralService.issueAccessToken(userId, "CUSTOMER", false, null);

        Map<String, Object> claims = ephemeralService.parseAndVerifyToken(token);
        assertThat(claims.get("sub")).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("Reject invalid non-existent private key path")
    void rejectInvalidPrivateKeyPath() {
        assertThatThrownBy(() -> new JwtTokenService("/non/existent/path/to/key.pem", objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }
}
