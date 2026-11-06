package com.swiftdrop.common.util;

import java.util.Optional;
import java.util.regex.Pattern;

/** Normalises Sri Lankan phone numbers to E.164 (FR-AUTH-001, BR-11). */
public final class PhoneNumberNormalizer {

    public static final Pattern E164 = Pattern.compile("^\\+94[1-9][0-9]{8}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().\\[\\]]");

    private PhoneNumberNormalizer() {
    }

    /** Accepts 07XXXXXXXX, +947XXXXXXXX, 947XXXXXXXX and landline forms; empty if invalid. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = SEPARATORS.matcher(raw.trim()).replaceAll("");
        String candidate;
        if (s.startsWith("+94")) {
            candidate = s;
        } else if (s.startsWith("94")) {
            candidate = "+" + s;
        } else if (s.startsWith("0")) {
            candidate = "+94" + s.substring(1);
        } else {
            return Optional.empty();
        }
        return E164.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
    }
}
