package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiQuestionClientTests {

    private static final String SERVICE_TOKEN = "unit-test-service-token";

    private MockRestServiceServer server;
    private ObjectMapper objectMapper;
    private AiQuestionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service.test");
        server = MockRestServiceServer.bindTo(builder).build();
        objectMapper = AiClientConfiguration.strictContractObjectMapper(
                new ObjectMapper().findAndRegisterModules()
        );
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        client = new AiQuestionClient(builder.build(), objectMapper, validator, SERVICE_TOKEN);
    }

    @Test
    void generateQuestionSendsContractHeadersAndValidatesResponse() {
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SERVICE_TOKEN))
                .andExpect(header("X-Request-Id", "req_question_001"))
                .andExpect(header("X-Correlation-Id", "corr_interview_001"))
                .andExpect(header("Idempotency-Key", "question-operation-001"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(validRequestJson(), JsonCompareMode.STRICT))
                .andRespond(withSuccess(validResponseJson(), MediaType.APPLICATION_JSON));

        QuestionGenerationModels.Response response = client.generateQuestion(
                QuestionGenerationModelsTests.validRequest(),
                validContext()
        );

        assertEquals("Spring Boot", response.topic());
        assertEquals("question-v1", response.metadata().promptVersion());
        assertEquals(QuestionGenerationModels.Difficulty.MEDIUM, response.difficulty());
        server.verify();
    }

    @Test
    void generateQuestionMapsSocketTimeoutToStableClientError() {
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("simulated timeout");
                });

        AiClientException error = assertThrows(
                AiClientException.class,
                () -> client.generateQuestion(
                        QuestionGenerationModelsTests.validRequest(),
                        validContext()
                )
        );

        assertEquals(AiClientException.Kind.TIMEOUT, error.kind());
        assertEquals("AI_TIMEOUT", error.errorCode());
        assertEquals("req_question_001", error.requestId());
        server.verify();
    }

    @Test
    void generateQuestionRejectsUnknownOrMissingResponseFields() {
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andRespond(withSuccess(
                        "{\"question\":\"invalid\",\"unexpected\":true}",
                        MediaType.APPLICATION_JSON
                ));

        AiClientException error = assertThrows(
                AiClientException.class,
                () -> client.generateQuestion(
                        QuestionGenerationModelsTests.validRequest(),
                        validContext()
                )
        );

        assertEquals(AiClientException.Kind.INVALID_RESPONSE, error.kind());
        assertEquals("INVALID_AI_RESPONSE", error.errorCode());
        assertEquals(200, error.httpStatus());
        server.verify();
    }

    @Test
    void generateQuestionRejectsMissingRequiredNumericResponseField() throws Exception {
        ObjectNode responseBody = (ObjectNode) objectMapper.readTree(validResponseJson());
        ((ObjectNode) responseBody.get("metadata")).remove("latency_ms");
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(responseBody),
                        MediaType.APPLICATION_JSON
                ));

        AiClientException error = assertThrows(
                AiClientException.class,
                () -> client.generateQuestion(
                        QuestionGenerationModelsTests.validRequest(),
                        validContext()
                )
        );

        assertEquals(AiClientException.Kind.INVALID_RESPONSE, error.kind());
        assertEquals("req_question_001", error.requestId());
        server.verify();
    }

    @Test
    void generateQuestionRejectsCoercedNumericResponseField() throws Exception {
        ObjectNode responseBody = (ObjectNode) objectMapper.readTree(validResponseJson());
        ((ObjectNode) responseBody.get("metadata")).put("latency_ms", "0");
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(responseBody),
                        MediaType.APPLICATION_JSON
                ));

        AiClientException error = assertThrows(
                AiClientException.class,
                () -> client.generateQuestion(
                        QuestionGenerationModelsTests.validRequest(),
                        validContext()
                )
        );

        assertEquals(AiClientException.Kind.INVALID_RESPONSE, error.kind());
        assertEquals("req_question_001", error.requestId());
        server.verify();
    }

    @Test
    void generateQuestionRejectsNullSuccessPayload() {
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        AiClientException error = assertThrows(
                AiClientException.class,
                () -> client.generateQuestion(
                        QuestionGenerationModelsTests.validRequest(),
                        validContext()
                )
        );

        assertEquals(AiClientException.Kind.INVALID_RESPONSE, error.kind());
        assertEquals("req_question_001", error.requestId());
        server.verify();
    }

    @Test
    void generateQuestionMapsRemoteGatewayTimeoutError() {
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "LLM_TIMEOUT",
                                  "message": "The question provider timed out.",
                                  "request_id": "req_python_001",
                                  "timestamp": "2026-10-05T00:00:00Z",
                                  "details": {
                                    "capability": "question_generation",
                                    "retryable": true
                                  }
                                }
                                """));

        AiClientException error = assertThrows(
                AiClientException.class,
                () -> client.generateQuestion(
                        QuestionGenerationModelsTests.validRequest(),
                        validContext()
                )
        );

        assertEquals(AiClientException.Kind.TIMEOUT, error.kind());
        assertEquals("LLM_TIMEOUT", error.errorCode());
        assertEquals(504, error.httpStatus());
        assertEquals("req_python_001", error.requestId());
        server.verify();
    }

    @Test
    void generateQuestionRejectsNonJsonRemoteError() {
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("unauthorized"));

        AiClientException error = assertThrows(
                AiClientException.class,
                () -> client.generateQuestion(
                        QuestionGenerationModelsTests.validRequest(),
                        validContext()
                )
        );

        assertEquals(AiClientException.Kind.INVALID_RESPONSE, error.kind());
        assertEquals(401, error.httpStatus());
        assertEquals("req_question_001", error.requestId());
        server.verify();
    }

    @Test
    void generateQuestionRejectsUnknownRemoteErrorCode() {
        server.expect(requestTo("http://ai-service.test/internal/v1/questions/generate"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "UNKNOWN_ERROR",
                                  "message": "Unknown error.",
                                  "request_id": "req_python_001",
                                  "timestamp": "2026-10-05T00:00:00Z",
                                  "details": {}
                                }
                                """));

        AiClientException error = assertThrows(
                AiClientException.class,
                () -> client.generateQuestion(
                        QuestionGenerationModelsTests.validRequest(),
                        validContext()
                )
        );

        assertEquals(AiClientException.Kind.INVALID_RESPONSE, error.kind());
        assertEquals("INVALID_AI_RESPONSE", error.errorCode());
        assertEquals("req_question_001", error.requestId());
        server.verify();
    }

    private AiRequestContext validContext() {
        return new AiRequestContext(
                "req_question_001",
                "corr_interview_001",
                "question-operation-001"
        );
    }

    private String validRequestJson() {
        return """
                {
                  "target_position": "Java Backend Engineer",
                  "skills": ["Java", "Spring Boot", "MySQL"],
                  "difficulty": "MEDIUM",
                  "question_number": 2,
                  "total_question_count": 5,
                  "previous_questions": [
                    {
                      "question_number": 1,
                      "question": "什么是事务隔离级别？",
                      "topic": "Transaction",
                      "question_type": "CONCEPTUAL"
                    }
                  ],
                  "language": "zh-CN"
                }
                """;
    }

    private String validResponseJson() {
        return """
                {
                  "question": "请说明 Spring Boot 在 Java Backend Engineer 工作中的核心作用，并给出一个实际示例。",
                  "topic": "Spring Boot",
                  "difficulty": "MEDIUM",
                  "expected_points": [
                    "准确说明 Spring Boot 的核心概念",
                    "给出与目标岗位相关的实际示例"
                  ],
                  "question_type": "CONCEPTUAL",
                  "metadata": {
                    "llm_provider": "mock",
                    "model_name": "deterministic-question-provider",
                    "prompt_version": "question-v1",
                    "token_usage": {
                      "input_tokens": 0,
                      "output_tokens": 0,
                      "total_tokens": 0
                    },
                    "latency_ms": 0
                  }
                }
                """;
    }
}
