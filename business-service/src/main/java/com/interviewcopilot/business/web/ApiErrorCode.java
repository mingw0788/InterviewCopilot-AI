package com.interviewcopilot.business.web;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    ILLEGAL_INTERVIEW_STATE(HttpStatus.CONFLICT),
    INTERVIEW_NOT_READY(HttpStatus.CONFLICT),
    INTERVIEW_NOT_COMPLETED(HttpStatus.CONFLICT),
    DUPLICATE_SUBMISSION(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
    OPERATION_IN_PROGRESS(HttpStatus.CONFLICT),
    REPORT_NOT_READY(HttpStatus.CONFLICT),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    LLM_PROVIDER_ERROR(HttpStatus.SERVICE_UNAVAILABLE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INVALID_AI_RESPONSE(HttpStatus.SERVICE_UNAVAILABLE),
    SCHEMA_VALIDATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
    NETWORK_ERROR(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ApiErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
