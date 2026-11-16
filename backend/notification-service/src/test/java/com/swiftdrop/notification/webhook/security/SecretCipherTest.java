package com.swiftdrop.notification.webhook.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("TST-013")
@Tag("FR-WHK-008")
class SecretCipherTest {

    private String validBase64Key;
    private SecretCipher cipher;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        validBase64Key = Base64.getEncoder().encodeToString(keyBytes);
        cipher = new SecretCipher(validBase64Key);
    }

    @Test
    @DisplayName("TST-013: AES-256-GCM round trip successfully encrypts and decrypts")
    void encryptDecrypt_roundTrip() {
        String secret = "whsec_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890abcdefg";

        byte[] encrypted = cipher.encrypt(secret);
        assertThat(encrypted).isNotNull();
        // 12 bytes nonce + 49 bytes plaintext + 16 bytes tag = 77 bytes
        assertThat(encrypted.length).isGreaterThan(12 + 16);

        String decrypted = cipher.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(secret);
    }

    @Test
    @DisplayName("TST-013: Nonce uniqueness - encrypting the same text twice produces distinct ciphertexts")
    void encrypt_nonceUniqueness() {
        String secret = "whsec_testSecret12345";
        Set<String> nonces = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            byte[] encrypted = cipher.encrypt(secret);
            byte[] nonce = Arrays.copyOfRange(encrypted, 0, 12);
            String nonceHex = Base64.getEncoder().encodeToString(nonce);
            assertThat(nonces.add(nonceHex)).isTrue();
        }
    }

    @Test
    @DisplayName("TST-013: Tampered ciphertext or authentication tag fails to decrypt")
    void decrypt_tamperedCiphertext_fails() {
        String secret = "whsec_originalSecret12345";
        byte[] encrypted = cipher.encrypt(secret);

        // Tamper with the last byte (authentication tag)
        encrypted[encrypted.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    @DisplayName("TST-013: Tampered nonce fails authentication tag check")
    void decrypt_tamperedNonce_fails() {
        String secret = "whsec_originalSecret12345";
        byte[] encrypted = cipher.encrypt(secret);

        // Tamper with the nonce
        encrypted[0] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    @DisplayName("FR-WHK-008: Startup fails fast when key is missing or blank")
    void constructor_missingKey_failsFast() {
        assertThatThrownBy(() -> new SecretCipher(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK_SECRET_ENCRYPTION_KEY must be provided");

        assertThatThrownBy(() -> new SecretCipher("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK_SECRET_ENCRYPTION_KEY must be provided");
    }

    @Test
    @DisplayName("FR-WHK-008: Startup fails fast when key is not valid Base64")
    void constructor_invalidBase64_failsFast() {
        assertThatThrownBy(() -> new SecretCipher("not-valid-base-64!@#$"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be valid Base64");
    }

    @Test
    @DisplayName("FR-WHK-008: Startup fails fast when key length is not exactly 32 bytes (256 bits)")
    void constructor_wrongKeyLength_failsFast() {
        // 16 bytes (128 bits)
        String key16Bytes = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new SecretCipher(key16Bytes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to exactly 32 bytes (256 bits), got 16");

        // 24 bytes (192 bits)
        String key24Bytes = Base64.getEncoder().encodeToString(new byte[24]);
        assertThatThrownBy(() -> new SecretCipher(key24Bytes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to exactly 32 bytes (256 bits), got 24");
    }
}

