package com.swiftdrop.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.common.testapp.TestApp;
import com.swiftdrop.common.testsupport.TestTokens;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("TST-013")
class InternalApiKeyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "CHANGE_ME_CHANGE_ME_CHANGE_ME_CHANGE_ME", "changeme-changeme-changeme-changeme-x",
            "   "})
    @DisplayName("TST-013 start-up fails for a missing, short or placeholder INTERNAL_API_KEY")
    void weakKeysAreRejected(String value) {
        assertThatThrownBy(() -> new InternalApiKey(value)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TST-013 a strong key is accepted and never printed")
    void strongKey() {
        assertThatCode(() -> new InternalApiKey(TestApp.INTERNAL_KEY)).doesNotThrowAnyException();
        assertThat(new InternalApiKey(TestApp.INTERNAL_KEY).toString()).doesNotContain(TestApp.INTERNAL_KEY);
    }

    @Test
    @DisplayName("TST-013 the public key PEM (X.509) is parsed; garbage and a missing path fail clearly")
    void publicKeyPem() {
        RSAPublicKey key = TestTokens.create().publicKey();
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        assertThat(FileJwtPublicKeyProvider.parse(pem).getModulus()).isEqualTo(key.getModulus());
        assertThatThrownBy(() -> FileJwtPublicKeyProvider.parse("garbage")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FileJwtPublicKeyProvider(null).publicKey())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_PUBLIC_KEY_PATH");
    }
}
