package com.swiftdrop.auth.service;

import com.swiftdrop.auth.login.service.PasswordHasher;
import com.swiftdrop.auth.login.service.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TST-013 FR-AUTH-002: PasswordPolicy and PasswordHasher validation")
@Tag("TST-013")
class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    @DisplayName("TST-013: Valid strong password passes all checks")
    void validPasswordPasses() {
        List<String> violations = passwordPolicy.check("P@ssw0rdSecure!2026", "kasun.perera@example.lk");
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("TST-013: Password shorter than 10 characters is rejected")
    void shortPasswordRejected() {
        List<String> violations = passwordPolicy.check("Sh0rt!", "kasun@example.lk");
        assertThat(violations).anyMatch(v -> v.contains("between 10 and 64"));
    }

    @Test
    @DisplayName("TST-013: Password missing uppercase is rejected")
    void missingUppercaseRejected() {
        List<String> violations = passwordPolicy.check("password123!", "user@example.lk");
        assertThat(violations).anyMatch(v -> v.contains("uppercase"));
    }

    @Test
    @DisplayName("TST-013: Password missing lowercase is rejected")
    void missingLowercaseRejected() {
        List<String> violations = passwordPolicy.check("PASSWORD123!", "user@example.lk");
        assertThat(violations).anyMatch(v -> v.contains("lowercase"));
    }

    @Test
    @DisplayName("TST-013: Password missing digit is rejected")
    void missingDigitRejected() {
        List<String> violations = passwordPolicy.check("PasswordSpecial!", "user@example.lk");
        assertThat(violations).anyMatch(v -> v.contains("digit"));
    }

    @Test
    @DisplayName("TST-013: Password missing special character is rejected")
    void missingSpecialRejected() {
        List<String> violations = passwordPolicy.check("Password12345", "user@example.lk");
        assertThat(violations).anyMatch(v -> v.contains("special character"));
    }

    @Test
    @DisplayName("TST-013: Password containing email local-part is rejected case-insensitively")
    void containingEmailLocalPartRejected() {
        List<String> violations = passwordPolicy.check("KasunSecure123!", "KASUN@example.lk");
        assertThat(violations).anyMatch(v -> v.contains("email local-part"));
    }

    @Test
    @DisplayName("TST-013: PasswordHasher hashes with BCrypt cost 12 and verifies matches")
    void passwordHasherValidates() {
        String raw = "StrongP@ssw0rd!123";
        String hash = passwordHasher.hash(raw);
        assertThat(hash).startsWith("$2a$12$");
        assertThat(passwordHasher.matches(raw, hash)).isTrue();
        assertThat(passwordHasher.matches("WrongPassword!123", hash)).isFalse();
    }
}
