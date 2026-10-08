package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.interviewcopilot.business.integration.ai.QuestionGenerationModels.Difficulty.MEDIUM;
import static com.interviewcopilot.business.integration.ai.QuestionGenerationModels.QuestionType.CONCEPTUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionGenerationModelsTests {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestSerializesWithFrozenSnakeCaseFieldNames() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(validRequest()));

        assertEquals("Java Backend Engineer", json.get("target_position").asText());
        assertEquals(2, json.get("question_number").asInt());
        assertEquals(5, json.get("total_question_count").asInt());
        assertEquals("CONCEPTUAL", json.at("/previous_questions/0/question_type").asText());
        assertFalse(json.has("targetPosition"));
    }

    @Test
    void requestValidationRejectsDuplicateSkillsAndInvalidQuestionNumber() {
        QuestionGenerationModels.Request request = new QuestionGenerationModels.Request(
                "Java Backend Engineer",
                List.of("Java", "Java"),
                MEDIUM,
                6,
                5,
                List.of(),
                "zh-CN"
        );

        var violations = validator.validate(request);

        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(item ->
                item.getMessage().contains("unique")));
        assertTrue(violations.stream().anyMatch(item ->
                item.getMessage().contains("totalQuestionCount")));
    }

    @Test
    void responseValidationRejectsDuplicateExpectedPoints() {
        QuestionGenerationModels.Response response = new QuestionGenerationModels.Response(
                "请解释 Spring Bean 生命周期。",
                "Spring",
                MEDIUM,
                List.of("生命周期", "生命周期"),
                CONCEPTUAL,
                new QuestionGenerationModels.Metadata(
                        "mock",
                        "deterministic-question-provider",
                        "question-v1",
                        new QuestionGenerationModels.TokenUsage(0, 0, 0),
                        0L
                )
        );

        assertTrue(validator.validate(response).stream().anyMatch(item ->
                item.getMessage().contains("unique")));
    }

    @Test
    void requestContextRejectsUnsafeIdempotencyKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiRequestContext("request", "correlation", "contains space")
        );
    }

    static QuestionGenerationModels.Request validRequest() {
        return new QuestionGenerationModels.Request(
                "Java Backend Engineer",
                List.of("Java", "Spring Boot", "MySQL"),
                MEDIUM,
                2,
                5,
                List.of(new QuestionGenerationModels.PreviousQuestion(
                        1,
                        "什么是事务隔离级别？",
                        "Transaction",
                        CONCEPTUAL
                )),
                "zh-CN"
        );
    }
}
