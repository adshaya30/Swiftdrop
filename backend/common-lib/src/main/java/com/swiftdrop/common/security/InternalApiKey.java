package com.swiftdrop.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** The INTERNAL_API_KEY secret. Construction fails fast if it is missing, short or a placeholder. */
public final class InternalApiKey {

    public static final int MIN_LENGTH = 32;

    private final String value;

    public InternalApiKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("INTERNAL_API_KEY is not set");
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() < MIN_LENGTH) {
            throw new IllegalStateException("INTERNAL_API_KEY must be at least " + MIN_LENGTH + " characters");
        }
        if (lower.startsWith("change_me") || lower.startsWith("changeme")
                || lower.equals("secret") || lower.equals("password")) {
            throw new IllegalStateException("INTERNAL_API_KEY is a placeholder value");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }

    byte[] bytes() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "InternalApiKey[****]";
    }
}
