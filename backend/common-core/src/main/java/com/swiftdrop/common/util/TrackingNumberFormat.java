package com.swiftdrop.common.util;

import java.util.regex.Pattern;

/** Tracking number format (FR-PAR-005): SD- plus 10 characters of the alphabet below. */
public final class TrackingNumberFormat {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final Pattern PATTERN = Pattern.compile("^SD-[0-9A-HJKMNP-TV-Z]{10}$");

    private TrackingNumberFormat() {
    }

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value).matches();
    }
}
