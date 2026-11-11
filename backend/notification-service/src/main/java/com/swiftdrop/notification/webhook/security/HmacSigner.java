package com.swiftdrop.notification.webhook.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Generates and signs webhook payloads using HMAC-SHA256 (FR-WHK-003).
 */
@Component
public class HmacSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String SECRET_PREFIX = "whsec_";
    private static final int SECRET_ENTROPY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a new webhook secret: {@code whsec_} + 43 URL-safe Base64 characters from 32 random bytes.
     *
     * @return the newly generated secret string
     */
    public String generateSecret() {
        byte[] randomBytes = new byte[SECRET_ENTROPY_BYTES];
        secureRandom.nextBytes(randomBytes);
        String urlSafeEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return SECRET_PREFIX + urlSafeEncoded;
    }

    /**
     * Computes the {@code X-SwiftDrop-Signature} header value: {@code sha256=<hex>},
     * which is HMAC-SHA256 of {@code <timestamp>.<raw body>} signed with {@code secret}.
     *
     * @param timestamp Unix epoch timestamp in seconds
     * @param rawBody   raw string body to sign
     * @param secret    the merchant's webhook secret
     * @return {@code sha256=<hex>}
     */
    public String sign(long timestamp, String rawBody, String secret) {
        if (rawBody == null || secret == null) {
            throw new IllegalArgumentException("rawBody and secret must not be null");
        }
        String toSign = timestamp + "." + rawBody;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hmacBytes = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_PREFIX + bytesToHex(hmacBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    /**
     * Constant-time comparison between computed signature and received signature header.
     *
     * @param expectedSignature signature computed by sender
     * @param providedSignature signature header provided by receiver/caller
     * @return true if equal
     */
    public boolean verify(String expectedSignature, String providedSignature) {
        if (expectedSignature == null || providedSignature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

