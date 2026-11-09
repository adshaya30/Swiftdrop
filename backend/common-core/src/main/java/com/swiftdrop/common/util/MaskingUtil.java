package com.swiftdrop.common.util;

/** Masks personal data for logs (BR-28). Null safe. */
public final class MaskingUtil {

    private MaskingUtil() {
    }

    /** +94771234567 becomes +94*****4567. */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String p = phone.trim();
        if (p.length() < 8) {
            return "****";
        }
        return p.substring(0, 3) + "*****" + p.substring(p.length() - 4);
    }

    /** john@example.com becomes j***@example.com. */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        String e = email.trim();
        int at = e.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return e.charAt(0) + "***" + e.substring(at);
    }
}
