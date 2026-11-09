package com.swiftdrop.auth.login.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt password hashing wrapper with cost factor 12 (FR-AUTH-002, NFR-SEC-010).
 * Never logs or returns hashes.
 */
@Component
public class PasswordHasher {

    public static final int BCRYPT_STRENGTH = 12;
    private final BCryptPasswordEncoder encoder;

    public PasswordHasher() {
        this.encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null) {
            return false;
        }
        return encoder.matches(rawPassword, encodedHash);
    }
}
