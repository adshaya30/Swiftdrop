package com.swiftdrop.notification.webhook.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM cipher for webhook secret encryption at rest (FR-WHK-008, SRS 9.2).
 * Fails fast at startup if WEBHOOK_SECRET_ENCRYPTION_KEY is missing, not valid Base64, or not 32 bytes.
 */
@Component
public class SecretCipher {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_NONCE_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_TAG_LENGTH_BYTES = 16;
    private static final int REQUIRED_KEY_LENGTH_BYTES = 32;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipher(@Value("${notification.webhook.secret-key:${WEBHOOK_SECRET_ENCRYPTION_KEY:}}") String base64Key) {
        if (base64Key == null || base64Key.trim().isEmpty()) {
            throw new IllegalStateException("WEBHOOK_SECRET_ENCRYPTION_KEY must be provided");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("WEBHOOK_SECRET_ENCRYPTION_KEY must be valid Base64", e);
        }
        if (keyBytes.length != REQUIRED_KEY_LENGTH_BYTES) {
            throw new IllegalStateException("WEBHOOK_SECRET_ENCRYPTION_KEY must decode to exactly 32 bytes (256 bits), got "
                    + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Encrypts plaintext secret using AES-256-GCM with a random 12-byte nonce.
     * Output format: 12-byte nonce + ciphertext + 16-byte authentication tag.
     *
     * @param plainText the secret to encrypt
     * @return encrypted byte array
     */
    public byte[] encrypt(String plainText) {
        if (plainText == null) {
            throw new IllegalArgumentException("plainText cannot be null");
        }
        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] cipherTextWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[nonce.length + cipherTextWithTag.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(cipherTextWithTag, 0, result, nonce.length, cipherTextWithTag.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt webhook secret", e);
        }
    }

    /**
     * Decrypts ciphertext byte array back to plaintext in memory.
     * Never logs the decrypted plaintext.
     *
     * @param encryptedBytes 12-byte nonce + ciphertext + tag
     * @return plaintext secret
     */
    public String decrypt(byte[] encryptedBytes) {
        if (encryptedBytes == null || encryptedBytes.length < GCM_NONCE_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("Invalid encrypted payload size");
        }
        byte[] nonce = Arrays.copyOfRange(encryptedBytes, 0, GCM_NONCE_LENGTH_BYTES);
        int cipherTextLength = encryptedBytes.length - GCM_NONCE_LENGTH_BYTES;

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            byte[] plainBytes = cipher.doFinal(encryptedBytes, GCM_NONCE_LENGTH_BYTES, cipherTextLength);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt webhook secret; ciphertext may be tampered or corrupted", e);
        }
    }
}

