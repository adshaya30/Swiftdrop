package com.swiftdrop.notification.webhook.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("TST-014")
@Tag("FR-WHK-003")
class HmacSignerTest {

    private HmacSigner signer;

    @BeforeEach
    void setUp() {
        signer = new HmacSigner();
    }

    @Test
    @DisplayName("FR-WHK-003: Secret format is whsec_ followed by 43 URL-safe characters")
    void generateSecret_format() {
        String secret = signer.generateSecret();
        assertThat(secret).startsWith("whsec_");
        assertThat(secret.length()).isEqualTo(6 + 43); // 49 chars

        String entropyPart = secret.substring("whsec_".length());
        assertThat(entropyPart).matches("^[A-Za-z0-9_-]{43}$");
    }

    @Test
    @DisplayName("TST-014: HMAC known vector matches exact expected hex output")
    void sign_knownVector() throws Exception {
        String secret = "whsec_test_secret_key_1234567890abcdef";
        long timestamp = 1762819200L;
        String rawBody = "{\"id\":\"11111111-2222-3333-4444-555555555555\",\"type\":\"parcel.status.changed\"}";

        String signature = signer.sign(timestamp, rawBody, secret);
        assertThat(signature).startsWith("sha256=");

        // Verify with Java standard javax.crypto.Mac independently
        String expectedPayload = timestamp + "." + rawBody;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expectedBytes = mac.doFinal(expectedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("sha256=");
        for (byte b : expectedBytes) {
            sb.append(String.format("%02x", b));
        }

        assertThat(signature).isEqualTo(sb.toString());
    }

    @Test
    @DisplayName("TST-014: Constant-time verify passes for valid signature and fails for tampered")
    void verify_constantTime() {
        String secret = signer.generateSecret();
        long timestamp = 1762819200L;
        String body = "{\"event\":\"test\"}";

        String signature = signer.sign(timestamp, body, secret);

        assertThat(signer.verify(signature, signature)).isTrue();
        assertThat(signer.verify(signature, signature + "tamper")).isFalse();
        assertThat(signer.verify(signature, "sha256=0000000000000000000000000000000000000000000000000000000000000000")).isFalse();
        assertThat(signer.verify(signature, null)).isFalse();
        assertThat(signer.verify(null, signature)).isFalse();
    }

    @Test
    @DisplayName("FR-WHK-003: Null arguments throw IllegalArgumentException")
    void sign_nullArguments_throwException() {
        assertThatThrownBy(() -> signer.sign(12345L, null, "whsec_secret"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> signer.sign(12345L, "body", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

