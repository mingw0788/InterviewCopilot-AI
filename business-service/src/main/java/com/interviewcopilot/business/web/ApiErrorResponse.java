package com.interviewcopilot.business.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        @JsonProperty("request_id") String requestId,
        Instant timestamp,
        Map<String, Object> details
) {
    public static ApiErrorResponse of(ApiErrorCode code, String message, HttpServletRequest request) {
        return of(code, message, Map.of(), request);
    }

    public static ApiErrorResponse of(
            ApiErrorCode code,
            String message,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        return new ApiErrorResponse(
                code.name(), message, RequestIds.resolve(request), Instant.now(), Map.copyOf(details));
    }
}
