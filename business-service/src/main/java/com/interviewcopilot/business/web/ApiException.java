package com.interviewcopilot.business.web;

import java.util.Map;

public final class ApiException extends RuntimeException {
    private final ApiErrorCode code;
    private final Map<String, Object> details;

    public ApiException(ApiErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ApiErrorCode code, String message, Map<String, Object> details) {
        super(requireMessage(message));
        this.code = java.util.Objects.requireNonNull(code, "code is required");
        this.details = Map.copyOf(java.util.Objects.requireNonNull(details, "details is required"));
    }

    public ApiErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank() || message.length() > 500) {
            throw new IllegalArgumentException("message must contain 1 to 500 characters");
        }
        return message;
    }
}
