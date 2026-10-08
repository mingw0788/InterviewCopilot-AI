package com.interviewcopilot.business.web;

import com.interviewcopilot.business.auth.AuthenticationFailedException;
import com.interviewcopilot.business.auth.InvalidLoginRequestException;
import com.interviewcopilot.business.integration.ai.AiClientException;
import com.interviewcopilot.business.interview.domain.DomainRuleViolationException;
import com.interviewcopilot.business.interview.domain.DomainValidationException;
import com.interviewcopilot.business.user.DuplicateRegistrationException;
import com.interviewcopilot.business.user.InvalidRegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Optional;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> api(ApiException exception, HttpServletRequest request) {
        return error(exception.code(), exception.getMessage(), exception.details(), request);
    }

    @ExceptionHandler({InvalidRegistrationException.class, InvalidLoginRequestException.class,
            DomainValidationException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ApiErrorResponse> validation(Exception exception, HttpServletRequest request) {
        return error(ApiErrorCode.VALIDATION_FAILED, safeValidationMessage(exception), request);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiErrorResponse> unauthorized(AuthenticationFailedException exception,
                                                   HttpServletRequest request) {
        return error(ApiErrorCode.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateRegistrationException.class)
    ResponseEntity<ApiErrorResponse> duplicate(DuplicateRegistrationException exception,
                                                HttpServletRequest request) {
        return error(ApiErrorCode.DUPLICATE_SUBMISSION, exception.getMessage(), request);
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    ResponseEntity<ApiErrorResponse> illegalState(DomainRuleViolationException exception,
                                                   HttpServletRequest request) {
        return error(ApiErrorCode.ILLEGAL_INTERVIEW_STATE, exception.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return error(ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found", request);
    }

    @ExceptionHandler(AiClientException.class)
    ResponseEntity<ApiErrorResponse> aiFailure(AiClientException exception, HttpServletRequest request) {
        ApiErrorCode code = remoteAiCode(exception.errorCode()).orElseGet(() -> switch (exception.kind()) {
            case TIMEOUT -> ApiErrorCode.AI_TIMEOUT;
            case UNAVAILABLE, REMOTE_ERROR -> ApiErrorCode.AI_SERVICE_UNAVAILABLE;
            case INVALID_RESPONSE -> ApiErrorCode.INVALID_AI_RESPONSE;
        });
        return error(code, safeAiMessage(code), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class, MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> invalidRequest(Exception exception, HttpServletRequest request) {
        return error(ApiErrorCode.INVALID_REQUEST, "Invalid request", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled API failure [requestId={}, exception={}]",
                RequestIds.resolve(request), exception.getClass().getName());
        return error(ApiErrorCode.INTERNAL_ERROR, "Internal server error", request);
    }

    private String safeValidationMessage(Exception exception) {
        if (exception instanceof InvalidRegistrationException
                || exception instanceof InvalidLoginRequestException
                || exception instanceof DomainValidationException) {
            return exception.getMessage();
        }
        return "Request validation failed";
    }

    private Optional<ApiErrorCode> remoteAiCode(String remoteCode) {
        if (remoteCode == null) {
            return Optional.empty();
        }
        return switch (remoteCode) {
            case "AI_TIMEOUT" -> Optional.of(ApiErrorCode.AI_TIMEOUT);
            case "LLM_TIMEOUT" -> Optional.of(ApiErrorCode.LLM_TIMEOUT);
            case "AI_SERVICE_UNAVAILABLE" -> Optional.of(ApiErrorCode.AI_SERVICE_UNAVAILABLE);
            case "LLM_PROVIDER_ERROR" -> Optional.of(ApiErrorCode.LLM_PROVIDER_ERROR);
            case "RATE_LIMITED" -> Optional.of(ApiErrorCode.RATE_LIMITED);
            case "INVALID_AI_RESPONSE" -> Optional.of(ApiErrorCode.INVALID_AI_RESPONSE);
            case "SCHEMA_VALIDATION_FAILED" -> Optional.of(ApiErrorCode.SCHEMA_VALIDATION_FAILED);
            case "NETWORK_ERROR" -> Optional.of(ApiErrorCode.NETWORK_ERROR);
            default -> Optional.empty();
        };
    }

    private String safeAiMessage(ApiErrorCode code) {
        return switch (code) {
            case AI_TIMEOUT, LLM_TIMEOUT -> "AI processing timed out";
            case RATE_LIMITED -> "AI rate limit exceeded";
            case INVALID_AI_RESPONSE, SCHEMA_VALIDATION_FAILED -> "AI service returned an invalid response";
            default -> "AI service is temporarily unavailable";
        };
    }

    private ResponseEntity<ApiErrorResponse> error(
            ApiErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        return error(code, message, java.util.Map.of(), request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            ApiErrorCode code,
            String message,
            java.util.Map<String, Object> details,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(code.httpStatus())
                .body(ApiErrorResponse.of(code, message, details, request));
    }
}
