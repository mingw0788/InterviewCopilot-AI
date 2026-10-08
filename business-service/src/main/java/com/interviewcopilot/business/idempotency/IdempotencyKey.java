package com.interviewcopilot.business.idempotency;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null
                || value.length() < 8
                || value.length() > 128
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 8 to 128 visible ASCII characters");
        }
    }
}
