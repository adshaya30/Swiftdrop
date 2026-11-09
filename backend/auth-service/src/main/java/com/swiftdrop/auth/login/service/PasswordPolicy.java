package com.swiftdrop.auth.login.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates passwords per FR-AUTH-002:
 * - 10-64 characters
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one digit
 * - At least one special character
 * - Must not contain the email local-part (case-insensitive)
 */
@Component
public class PasswordPolicy {

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^a-zA-Z0-9]");

    public List<String> check(String password, String email) {
        List<String> violations = new ArrayList<>();

        if (password == null || password.length() < 10 || password.length() > 64) {
            violations.add("Password must be between 10 and 64 characters in length");
        }

        if (password != null) {
            if (!UPPERCASE.matcher(password).find()) {
                violations.add("Password must contain at least one uppercase letter");
            }
            if (!LOWERCASE.matcher(password).find()) {
                violations.add("Password must contain at least one lowercase letter");
            }
            if (!DIGIT.matcher(password).find()) {
                violations.add("Password must contain at least one digit");
            }
            if (!SPECIAL.matcher(password).find()) {
                violations.add("Password must contain at least one special character");
            }
            if (email != null && email.contains("@")) {
                String localPart = email.split("@")[0].trim().toLowerCase();
                if (!localPart.isEmpty() && password.toLowerCase().contains(localPart)) {
                    violations.add("Password must not contain the email local-part");
                }
            }
        }

        return violations;
    }
}
