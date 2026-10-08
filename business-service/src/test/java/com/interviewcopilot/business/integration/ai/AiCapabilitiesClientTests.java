package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiCapabilitiesClientTests {
    private MockRestServiceServer server;
    private AiEvaluationClient evaluations;
    private AiReportClient reports;
    private final AiRequestContext context = new AiRequestContext("req-test", "corr-test", "test-key-123");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service.test");
        server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper mapper = AiClientConfiguration.strictContractObjectMapper(new ObjectMapper().findAndRegisterModules());
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        RestClient client = builder.build();
        evaluations = new AiEvaluationClient(client, mapper, validator, "test-token");
        reports = new AiReportClient(client, mapper, validator, "test-token");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mapsTimeoutsToSafeTypedErrors(boolean report) {
        expectPath(report).andRespond(request -> { throw new SocketTimeoutException("private provider detail"); });
        AiClientException error = assertThrows(AiClientException.class, () -> call(report));
        assertEquals("AI_TIMEOUT", error.errorCode());
        assertEquals(AiClientException.Kind.TIMEOUT, error.kind());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsNonJsonMalformedAndOversizedProviderOutput(boolean report) {
        expectPath(report).andRespond(withSuccess("<html>provider error</html>", MediaType.TEXT_HTML));
        assertEquals("INVALID_AI_RESPONSE", assertThrows(AiClientException.class, () -> call(report)).errorCode());
        server.verify();
        server.reset();
        expectPath(report).andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));
        assertEquals("INVALID_AI_RESPONSE", assertThrows(AiClientException.class, () -> call(report)).errorCode());
        server.verify();
        server.reset();
        expectPath(report).andRespond(withSuccess("x".repeat(1_048_577), MediaType.APPLICATION_JSON));
        assertEquals("INVALID_AI_RESPONSE", assertThrows(AiClientException.class, () -> call(report)).errorCode());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesStructuredProviderErrorCodes(boolean report) {
        expectPath(report).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"LLM_PROVIDER_ERROR\",\"message\":\"Provider unavailable\",\"request_id\":\"remote-req\",\"timestamp\":\"2026-10-08T00:00:00Z\",\"details\":{}}"));
        AiClientException error = assertThrows(AiClientException.class, () -> call(report));
        assertEquals("LLM_PROVIDER_ERROR", error.errorCode());
        assertEquals("remote-req", error.requestId());
        server.verify();
    }

    private org.springframework.test.web.client.ResponseActions expectPath(boolean report) {
        return server.expect(requestTo("http://ai-service.test/internal/v1/" + (report ? "reports/generate" : "evaluations/evaluate")))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(header("Idempotency-Key", "test-key-123"));
    }

    private void call(boolean report) {
        if (report) {
            BigDecimal score = BigDecimal.valueOf(80);
            reports.generateReport(new ReportGenerationModels.Request("Java", List.of("Java"), ReportGenerationModels.Difficulty.MEDIUM,
                    new ReportGenerationModels.Metrics(score, 3, 3, score, score, score, score, 60),
                    List.of(new ReportGenerationModels.EvaluationSummary(1, score, List.of("clear"), List.of("depth"), "feedback")), "zh-CN"), context);
        } else {
            evaluations.evaluate(new AnswerEvaluationModels.Request("Java", List.of("Java"), AnswerEvaluationModels.Difficulty.MEDIUM,
                    new AnswerEvaluationModels.Question("Explain transactions", "Java", AnswerEvaluationModels.Difficulty.MEDIUM,
                            List.of("isolation"), AnswerEvaluationModels.QuestionType.CONCEPTUAL), "A concrete answer",
                    new AnswerEvaluationModels.ScoringRubric("correctness", "coverage", "depth", "clarity"), "zh-CN"), context);
        }
    }
}
