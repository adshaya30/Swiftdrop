package com.swiftdrop.auth.login.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.security.JwtPublicKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * RS256 JWT service adhering to SRS v2.1 Section 3.6 and Master Prompt Section 8.1.
 * Header: kid=swiftdrop1
 * Claims: sub, role, typ, mid (conditional), scope (conditional), pwc (conditional),
 *         iss=swiftdrop, aud=swiftdrop-api, iat, exp, jti.
 */
@Service
public class JwtService implements JwtPublicKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String KEY_ID = "swiftdrop1";
    public static final String ISSUER = "swiftdrop";
    public static final String AUDIENCE = "swiftdrop-api";
    public static final String TYPE_USER = "user";
    public static final String TYPE_CLIENT = "client";

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final ObjectMapper objectMapper;
    private final long accessTokenTtlMinutes;

    public JwtService(String privateKeyPath, ObjectMapper objectMapper) {
        this(privateKeyPath, 15L, objectMapper);
    }

    public JwtService(
            @Value("${jwt.private-key-path:${JWT_PRIVATE_KEY_PATH:}}") String privateKeyPath,
            @Value("${jwt.access-ttl-minutes:15}") long accessTokenTtlMinutes,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.privateKey = loadPrivateKey(privateKeyPath);
        this.publicKey = derivePublicKey(this.privateKey);
        log.info("JwtService initialized successfully with RS256 key kid={}", KEY_ID);
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenTtlMinutes * 60L;
    }

    /**
     * Compatibility helper for services/tests invoking by primitives.
     */
    public String issueAccessToken(UUID userId, String role, boolean mustChangePassword, UUID merchantId) {
        User u = new User();
        u.setId(userId);
        u.setRole(com.swiftdrop.auth.entity.UserRole.valueOf(role));
        u.setMustChangePassword(mustChangePassword);
        Merchant m = null;
        if (merchantId != null) {
            m = new Merchant();
            m.setId(merchantId);
        }
        return issueUserToken(u, m);
    }

    /**
     * Issues an RS256 signed access token for a user.
     * Claims: sub, role, typ=user, mid (only if MERCHANT), pwc=true (only if mustChangePassword),
     *         iss=swiftdrop, aud=swiftdrop-api, iat, exp, jti.
     * Never contains personal user data (email, phone, name).
     */
    public String issueUserToken(User user, Merchant merchant) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(getAccessTokenExpirySeconds());

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("role", user.getRole().name());
        claims.put("typ", TYPE_USER);

        if ("MERCHANT".equalsIgnoreCase(user.getRole().name()) && merchant != null) {
            claims.put("mid", merchant.getId().toString());
        }

        if (user.isMustChangePassword()) {
            claims.put("pwc", true);
        }

        claims.put("iss", ISSUER);
        claims.put("aud", AUDIENCE);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiry.getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());

        return signToken(claims);
    }

    /**
     * Issues an RS256 signed access token for an API client (M2M / Merchant API).
     */
    public String issueClientToken(Merchant merchant, UUID userId, Set<String> scopes) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(getAccessTokenExpirySeconds());

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId != null ? userId.toString() : merchant.getUserId().toString());
        claims.put("role", "MERCHANT");
        claims.put("typ", TYPE_CLIENT);
        claims.put("mid", merchant.getId().toString());

        if (scopes != null && !scopes.isEmpty()) {
            claims.put("scope", String.join(" ", scopes));
        }

        claims.put("iss", ISSUER);
        claims.put("aud", AUDIENCE);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiry.getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());

        return signToken(claims);
    }

    private String signToken(Map<String, Object> claims) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");
            header.put("kid", KEY_ID);

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String headerEncoded = encoder.encodeToString(objectMapper.writeValueAsBytes(header));
            String claimsEncoded = encoder.encodeToString(objectMapper.writeValueAsBytes(claims));
            String signingInput = headerEncoded + "." + claimsEncoded;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            byte[] signatureBytes = signature.sign();
            String signatureEncoded = encoder.encodeToString(signatureBytes);

            return signingInput + "." + signatureEncoded;
        } catch (Exception e) {
            log.error("Failed to sign access token", e);
            throw new IllegalStateException("Failed to generate access token", e);
        }
    }

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

    @Override
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
            RSAPrivateKey key = (RSAPrivateKey) kf.generatePrivate(spec);

            if (key.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT private key bit length must be at least 2048 bits");
            }

            return key;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT private key from " + pathStr + ": " + e.getMessage(), e);
        }
    }

    private RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) {
        try {
            if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException("Private key is not an RSAPrivateCrtKey; cannot derive public key");
            }
            RSAPublicKeySpec spec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive RSA public key from private key: " + e.getMessage(), e);
        }
    }
}
