package com.interviewcopilot.business.user;

public final class CredentialPolicy {
    private CredentialPolicy() {
    }

    public static String normalizeLoginIdentifier(String value) {
        if (value == null) {
            throw new Violation("Login identifier is required");
        }
        String normalized = value.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 3 || length > 50 || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new Violation("Login identifier must be 3 to 50 characters without control characters");
        }
        return normalized;
    }

    public static void validatePassword(String value) {
        int length = value == null ? 0 : value.codePointCount(0, value.length());
        if (length < 8 || length > 128) {
            throw new Violation("Password must be 8 to 128 characters");
        }
    }

    public static final class Violation extends RuntimeException {
        private Violation(String message) {
            super(message);
        }
    }
}
