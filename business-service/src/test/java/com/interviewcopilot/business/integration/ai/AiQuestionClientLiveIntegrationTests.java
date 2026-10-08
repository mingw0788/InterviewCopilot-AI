package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@EnabledIfEnvironmentVariable(named = "AI_INTEGRATION_BASE_URL", matches = ".+")
class AiQuestionClientLiveIntegrationTests {

    @Test
    void javaClientCallsRunningPythonQuestionEndpoint() {
        String baseUrl = System.getenv("AI_INTEGRATION_BASE_URL");
        String serviceToken = System.getenv("INTERNAL_AI_SERVICE_TOKEN");
        assertFalse(serviceToken == null || serviceToken.isBlank());

        AiServiceProperties properties = new AiServiceProperties(
                URI.create(baseUrl),
                serviceToken,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );
        AiQuestionClient client = new AiClientConfiguration().aiQuestionClient(
                RestClient.builder(),
                new ObjectMapper().findAndRegisterModules(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                properties
        );

        QuestionGenerationModels.Response response = client.generateQuestion(
                QuestionGenerationModelsTests.validRequest(),
                new AiRequestContext(
                        "req_live_question_001",
                        "corr_live_interview_001",
                        "live-question-operation-001"
                )
        );

        assertEquals("Spring Boot", response.topic());
        assertEquals("mock", response.metadata().llmProvider());
        assertEquals("question-v1", response.metadata().promptVersion());
    }
}
