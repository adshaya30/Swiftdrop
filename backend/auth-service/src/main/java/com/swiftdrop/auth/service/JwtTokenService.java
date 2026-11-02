package com.swiftdrop.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * Service for issuing and validating RS256 JWT access tokens.
 * SRS v2.1 – Section 3.6, FR-AUTH-003, FR-AUTH-006
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private static final String KEY_ID = "swiftdrop-1";
    private static final String ISSUER = "swiftdrop";
    private static final String AUDIENCE = "swiftdrop-api";
    private static final String TOKEN_TYPE = "user";
    public static final long ACCESS_TOKEN_EXPIRY_SECONDS = 900; // 15 minutes

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final ObjectMapper objectMapper;

    public JwtTokenService(
            @Value("${jwt.private-key-path:${JWT_PRIVATE_KEY_PATH:}}") String privateKeyPath,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.privateKey = loadPrivateKey(privateKeyPath);
        this.publicKey = derivePublicKey(this.privateKey);
        log.info("JwtTokenService initialized successfully with RS256 key kid={}", KEY_ID);
    }

    /**
     * Issues an RS256 signed access token for the given user.
     * Claims: sub, role, typ=user, iss=swiftdrop, aud=swiftdrop-api, iat, exp, jti, and conditional pwc/mid.
     * No sensitive user data (email, phone, name) is included.
     */
    public String issueAccessToken(UUID userId, String role, boolean mustChangePassword, UUID merchantId) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(ACCESS_TOKEN_EXPIRY_SECONDS);

            // 1. Header
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");
            header.put("kid", KEY_ID);

            // 2. Claims
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", userId.toString());
            claims.put("role", role);
            claims.put("typ", TOKEN_TYPE);
            claims.put("iss", ISSUER);
            claims.put("aud", AUDIENCE);
            claims.put("iat", now.getEpochSecond());
            claims.put("exp", expiry.getEpochSecond());
            claims.put("jti", UUID.randomUUID().toString());

            if (mustChangePassword) {
                claims.put("pwc", true);
            }
            if (merchantId != null) {
                claims.put("mid", merchantId.toString());
            }

            // 3. Serialize and Base64URL encode
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String headerEncoded = encoder.encodeToString(objectMapper.writeValueAsBytes(header));
            String claimsEncoded = encoder.encodeToString(objectMapper.writeValueAsBytes(claims));
            String signingInput = headerEncoded + "." + claimsEncoded;

            // 4. Sign with RS256 (SHA256withRSA)
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            byte[] signatureBytes = signature.sign();
            String signatureEncoded = encoder.encodeToString(signatureBytes);

            return signingInput + "." + signatureEncoded;
        } catch (Exception e) {
            log.error("Failed to sign access token for user {}", userId, e);
            throw new IllegalStateException("Failed to generate access token", e);
        }
    }

    /**
     * Verifies the RS256 signature and returns the parsed payload claims.
     */
    public Map<String, Object> parseAndVerifyToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT structure");
            }

            Base64.Decoder decoder = Base64.getUrlDecoder();
            String signingInput = parts[0] + "." + parts[1];
            byte[] signatureBytes = decoder.decode(parts[2]);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));

            if (!signature.verify(signatureBytes)) {
                throw new SecurityException("JWT signature verification failed");
            }

            byte[] claimsBytes = decoder.decode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(claimsBytes, Map.class);
            return claims;
        } catch (Exception e) {
            throw new SecurityException("Failed to verify access token: " + e.getMessage(), e);
        }
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    private RSAPrivateKey loadPrivateKey(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            throw new IllegalStateException(
                    "JWT private signing key configuration is missing. " +
                    "Set 'jwt.private-key-path' or environment variable 'JWT_PRIVATE_KEY_PATH'. " +
                    "Ephemeral key generation is forbidden in auth-service per SRS Section 3.6.");
        }

        try {
            byte[] keyBytes;
            if (pathStr.startsWith("classpath:")) {
                String resourcePath = pathStr.substring("classpath:".length());
                if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is == null) {
                        throw new IllegalStateException("JWT private key not found on classpath: " + pathStr);
                    }
                    keyBytes = is.readAllBytes();
                }
            } else {
                Path path = Path.of(pathStr);
                if (!Files.exists(path)) {
                    Path altPath = Path.of("backend/auth-service").resolve(pathStr);
                    if (Files.exists(altPath)) {
                        path = altPath;
                    } else {
                        throw new IllegalStateException("JWT private key file does not exist at: " + pathStr);
                    }
                }
                keyBytes = Files.readAllBytes(path);
            }

            String pem = new String(keyBytes, StandardCharsets.UTF_8);
            String privateKeyPEM = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA private key from " + pathStr + ": " + e.getMessage(), e);
        }
    }

    private RSAPublicKey derivePublicKey(RSAPrivateKey privKey) {
        try {
            if (privKey instanceof RSAPrivateCrtKey crtKey) {
                RSAPublicKeySpec spec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return (RSAPublicKey) kf.generatePublic(spec);
            }
            throw new IllegalStateException("Private key is not an RSAPrivateCrtKey; cannot derive public key");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive RSA public key: " + e.getMessage(), e);
        }
    }
}
