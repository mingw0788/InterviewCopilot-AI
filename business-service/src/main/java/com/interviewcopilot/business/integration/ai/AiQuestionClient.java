package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.interviewcopilot.business.integration.ai.QuestionGenerationModels.ErrorCode;
import static com.interviewcopilot.business.integration.ai.QuestionGenerationModels.Request;
import static com.interviewcopilot.business.integration.ai.QuestionGenerationModels.Response;

public final class AiQuestionClient {

    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final String QUESTION_PATH = "/internal/v1/questions/generate";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String serviceToken;

    AiQuestionClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            Validator validator,
            String serviceToken
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.serviceToken = serviceToken;
    }

    public Response generateQuestion(Request request, AiRequestContext context) {
        return exchange(QUESTION_PATH, request, Response.class, context);
    }

    <T> T exchange(String path, Object request, Class<T> responseType, AiRequestContext context) {
        validateRequest(request);
        if (context == null) {
            throw new IllegalArgumentException("AI request context is required.");
        }
        byte[] requestBody = serializeRequest(request);

        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .header("X-Request-Id", context.requestId())
                    .header("X-Correlation-Id", context.correlationId())
                    .header("Idempotency-Key", context.idempotencyKey())
                    .body(requestBody)
                    .exchange((ignoredRequest, response) ->
                            decodeResponse(response, context.requestId(), responseType));
        } catch (AiClientException error) {
            throw error;
        } catch (ResourceAccessException error) {
            if (hasTimeoutCause(error)) {
                throw clientError(
                        AiClientException.Kind.TIMEOUT,
                        "AI_TIMEOUT",
                        "The AI service request timed out.",
                        null,
                        context.requestId(),
                        error
                );
            }
            throw clientError(
                    AiClientException.Kind.UNAVAILABLE,
                    "NETWORK_ERROR",
                    "The AI service could not be reached.",
                    null,
                    context.requestId(),
                    error
            );
        }
    }

    private <T> T decodeResponse(ClientHttpResponse response, String localRequestId, Class<T> responseType) throws IOException {
        int status = response.getStatusCode().value();
        byte[] responseBody = readBoundedBody(response, localRequestId);
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            String responseKind = status == HttpStatus.OK.value() ? "success" : "error";
            throw invalidResponse(
                    "AI service " + responseKind + " response is not JSON.",
                    status,
                    localRequestId,
                    null
            );
        }
        if (status != HttpStatus.OK.value()) {
            throw mapRemoteError(status, responseBody, localRequestId);
        }

        try {
            T decoded = objectMapper.readValue(responseBody, responseType);
            if (decoded == null) {
                throw invalidResponse(
                        "AI service success response cannot be null.",
                        status,
                        localRequestId,
                        null
                );
            }
            Set<ConstraintViolation<T>> violations = validator.validate(decoded);
            if (!violations.isEmpty()) {
                throw invalidResponse(
                        "AI service success response violates the frozen schema: "
                                + violationPaths(violations),
                        status,
                        localRequestId,
                        null
                );
            }
            return decoded;
        } catch (JsonProcessingException error) {
            throw invalidResponse(
                    "AI service returned invalid structured JSON.",
                    status,
                    localRequestId,
                    error
            );
        }
    }

    private byte[] readBoundedBody(
            ClientHttpResponse response,
            String localRequestId
    ) throws IOException {
        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
        if (body.length > MAX_RESPONSE_BYTES) {
            throw invalidResponse(
                    "AI service response exceeds the allowed size.",
                    response.getStatusCode().value(),
                    localRequestId,
                    null
            );
        }
        return body;
    }

    private AiClientException mapRemoteError(int status, byte[] body, String localRequestId) {
        ErrorPayload errorPayload;
        try {
            errorPayload = objectMapper.readValue(body, ErrorPayload.class);
            if (errorPayload == null) {
                throw new IllegalArgumentException("Error response cannot be null.");
            }
            Set<ConstraintViolation<ErrorPayload>> violations = validator.validate(errorPayload);
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException(
                        "Error response violates the frozen schema: " + violationPaths(violations)
                );
            }
        } catch (IOException | IllegalArgumentException error) {
            return invalidResponse(
                    "AI service returned an invalid error response.",
                    status,
                    localRequestId,
                    error
            );
        }

        AiClientException.Kind kind = switch (status) {
            case 504 -> AiClientException.Kind.TIMEOUT;
            case 503 -> AiClientException.Kind.UNAVAILABLE;
            default -> AiClientException.Kind.REMOTE_ERROR;
        };
        return clientError(
                kind,
                errorPayload.code().name(),
                errorPayload.message(),
                status,
                errorPayload.requestId(),
                null
        );
    }

    private byte[] serializeRequest(Object request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("AI question request could not be serialized.", error);
        }
    }

    private void validateRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("AI question request is required.");
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI question request violates the frozen schema: " + violationPaths(violations)
            );
        }
    }

    private String violationPaths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private boolean hasTimeoutCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private AiClientException invalidResponse(
            String message,
            Integer status,
            String requestId,
            Throwable cause
    ) {
        return clientError(
                AiClientException.Kind.INVALID_RESPONSE,
                "INVALID_AI_RESPONSE",
                message,
                status,
                requestId,
                cause
        );
    }

    private AiClientException clientError(
            AiClientException.Kind kind,
            String code,
            String message,
            Integer status,
            String requestId,
            Throwable cause
    ) {
        return new AiClientException(kind, code, message, status, requestId, cause);
    }

    private record ErrorPayload(
            @NotNull ErrorCode code,
            @NotNull @Size(min = 1, max = 500) String message,
            @NotNull @Size(min = 1, max = 128) String requestId,
            @NotNull Instant timestamp,
            @NotNull Map<String, Object> details
    ) {
    }
}
