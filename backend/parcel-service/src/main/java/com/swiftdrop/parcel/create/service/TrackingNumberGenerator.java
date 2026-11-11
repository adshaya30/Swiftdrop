package com.swiftdrop.parcel.create.service;

import java.security.SecureRandom;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * FR-PAR-004, BR-11: cryptographically random candidates.
 * Database uniqueness and collision retries belong to the later creation transaction.
 */
@Component
public class TrackingNumberGenerator {
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private final SecureRandom random;

    public TrackingNumberGenerator() {
        this(new SecureRandom());
    }

    TrackingNumberGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random);
    }

    public String generate() {
        StringBuilder number = new StringBuilder("SD-");
        for (int i = 0; i < 10; i++) {
            number.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return number.toString();
    }
}
