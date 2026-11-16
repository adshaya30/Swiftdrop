package com.swiftdrop.auth.merchant.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of API credentials (SRS FR-AUTH-018). client_id is "sdc_" + 24 random characters and
 * client_secret is "sds_" + 43 URL-safe characters from 32 random bytes. Only the SHA-256 hex of the secret is stored.
 */
public final class CredentialSecrets {

    public static final String CLIENT_ID_PREFIX = "sdc_";
    public static final String CLIENT_SECRET_PREFIX = "sds_";
    private static final String ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_LENGTH = 24;
    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialSecrets() {
    }

    public static String newClientId() {
        StringBuilder sb = new StringBuilder(CLIENT_ID_PREFIX);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String newClientSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return CLIENT_SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 as 64 lower-case hex characters. */
    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison of two hashes (BR-25, NFR-SEC). */
    public static boolean constantTimeEquals(String hashA, String hashB) {
        return MessageDigest.isEqual(hashA.getBytes(StandardCharsets.UTF_8), hashB.getBytes(StandardCharsets.UTF_8));
    }
}
