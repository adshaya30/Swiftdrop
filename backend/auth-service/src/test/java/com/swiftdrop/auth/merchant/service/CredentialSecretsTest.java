package com.swiftdrop.auth.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TST-013")
class CredentialSecretsTest {

    @Test
    @DisplayName("TST-013 FR-AUTH-018: client_id is sdc_ + 24 alphanumeric characters and fits VARCHAR(40)")
    void clientIdFormat() {
        for (int i = 0; i < 200; i++) {
            String id = CredentialSecrets.newClientId();
            assertThat(id).matches("^sdc_[A-Za-z0-9]{24}$");
            assertThat(id.length()).isLessThanOrEqualTo(40);
        }
    }

    @Test
    @DisplayName("TST-013 FR-AUTH-018: client_secret is sds_ + 43 URL-safe characters from 32 random bytes")
    void clientSecretFormat() {
        for (int i = 0; i < 200; i++) {
            assertThat(CredentialSecrets.newClientSecret()).matches("^sds_[A-Za-z0-9_-]{43}$");
        }
    }

    @Test
    @DisplayName("TST-013 FR-AUTH-018: generated ids and secrets do not repeat")
    void unique() {
        Set<String> ids = new HashSet<>();
        Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            ids.add(CredentialSecrets.newClientId());
            secrets.add(CredentialSecrets.newClientSecret());
        }
        assertThat(ids).hasSize(2000);
        assertThat(secrets).hasSize(2000);
    }

    @Test
    @DisplayName("TST-013 FR-AUTH-018: only a 64 character lower-case SHA-256 hex of the secret is stored")
    void hash() {
        assertThat(CredentialSecrets.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        String secret = CredentialSecrets.newClientSecret();
        String hash = CredentialSecrets.sha256Hex(secret);
        assertThat(hash).matches("^[0-9a-f]{64}$").doesNotContain(secret);
    }

    @Test
    @DisplayName("TST-013 FR-AUTH-019: hashes are compared with a constant-time comparison")
    void constantTimeCompare() {
        String a = CredentialSecrets.sha256Hex("one");
        assertThat(CredentialSecrets.constantTimeEquals(a, CredentialSecrets.sha256Hex("one"))).isTrue();
        assertThat(CredentialSecrets.constantTimeEquals(a, CredentialSecrets.sha256Hex("two"))).isFalse();
        assertThat(CredentialSecrets.constantTimeEquals(a, "short")).isFalse();
    }
}
