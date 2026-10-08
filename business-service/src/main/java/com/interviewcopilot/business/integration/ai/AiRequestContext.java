package com.interviewcopilot.business.integration.ai;

public record AiRequestContext(
        String requestId,
        String correlationId,
        String idempotencyKey
) {

    public AiRequestContext {
        requireTraceHeader(requestId, "requestId");
        requireTraceHeader(correlationId, "correlationId");
        if (idempotencyKey == null
                || idempotencyKey.length() < 8
                || idempotencyKey.length() > 128
                || !idempotencyKey.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new IllegalArgumentException(
                    "idempotencyKey must contain 8 to 128 visible ASCII characters."
            );
        }
    }

    private static void requireTraceHeader(String value, String label) {
        if (value == null
                || value.isBlank()
                || value.length() > 128
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must contain 1 to 128 safe characters.");
        }
    }
}
