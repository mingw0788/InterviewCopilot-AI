package com.interviewcopilot.business.integration.ai;

public final class AiClientException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNAVAILABLE,
        REMOTE_ERROR,
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final String errorCode;
    private final Integer httpStatus;
    private final String requestId;

    AiClientException(
            Kind kind,
            String errorCode,
            String message,
            Integer httpStatus,
            String requestId,
            Throwable cause
    ) {
        super(message, cause);
        this.kind = kind;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.requestId = requestId;
    }

    public Kind kind() {
        return kind;
    }

    public String errorCode() {
        return errorCode;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String requestId() {
        return requestId;
    }
}
