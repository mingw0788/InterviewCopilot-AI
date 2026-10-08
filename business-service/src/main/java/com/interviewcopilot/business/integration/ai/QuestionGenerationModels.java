package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

public final class QuestionGenerationModels {

    private QuestionGenerationModels() {
    }

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public enum QuestionType {
        CONCEPTUAL,
        PRACTICAL,
        SCENARIO,
        DESIGN
    }

    public enum ErrorCode {
        INVALID_REQUEST,
        VALIDATION_FAILED,
        UNAUTHORIZED,
        FORBIDDEN,
        RESOURCE_NOT_FOUND,
        ILLEGAL_INTERVIEW_STATE,
        INTERVIEW_NOT_READY,
        INTERVIEW_NOT_COMPLETED,
        DUPLICATE_SUBMISSION,
        IDEMPOTENCY_KEY_REUSED,
        OPERATION_IN_PROGRESS,
        REPORT_NOT_READY,
        AI_TIMEOUT,
        LLM_TIMEOUT,
        AI_SERVICE_UNAVAILABLE,
        LLM_PROVIDER_ERROR,
        RATE_LIMITED,
        INVALID_AI_RESPONSE,
        SCHEMA_VALIDATION_FAILED,
        NETWORK_ERROR,
        INTERNAL_ERROR
    }

    public record PreviousQuestion(
            @Min(1) @Max(10) int questionNumber,
            @NotNull @Size(min = 1, max = 4000) String question,
            @NotNull @Size(min = 1, max = 200) String topic,
            @NotNull QuestionType questionType
    ) {
    }

    public record Request(
            @NotBlank
            @Size(max = 100)
            @Pattern(regexp = "^\\S(?:.*\\S)?$")
            String targetPosition,
            @NotEmpty
            @Size(max = 20)
            List<@NotBlank @Size(max = 50) @Pattern(regexp = "^\\S(?:.*\\S)?$") String> skills,
            @NotNull Difficulty difficulty,
            @Min(1) @Max(10) int questionNumber,
            @Min(3) @Max(10) int totalQuestionCount,
            @NotNull @Size(max = 9) List<@Valid PreviousQuestion> previousQuestions,
            @NotBlank @Pattern(regexp = "zh-CN") String language
    ) {
        public Request {
            skills = skills == null ? null : List.copyOf(skills);
            previousQuestions = previousQuestions == null ? null : List.copyOf(previousQuestions);
        }

        @AssertTrue(message = "questionNumber cannot exceed totalQuestionCount")
        @JsonIgnore
        public boolean isQuestionNumberWithinTotal() {
            return questionNumber <= totalQuestionCount;
        }

        @AssertTrue(message = "skills must contain unique values")
        @JsonIgnore
        public boolean isSkillsUnique() {
            return skills == null || skills.size() == new HashSet<>(skills).size();
        }
    }

    public record TokenUsage(
            @NotNull @Min(0) Integer inputTokens,
            @NotNull @Min(0) Integer outputTokens,
            @NotNull @Min(0) Integer totalTokens
    ) {
    }

    public record Metadata(
            @NotNull @Size(min = 1, max = 100) String llmProvider,
            @NotNull @Size(min = 1, max = 200) String modelName,
            @NotNull @Pattern(regexp = "question-v1") String promptVersion,
            @NotNull @Valid TokenUsage tokenUsage,
            @NotNull @Min(0) Long latencyMs
    ) {
    }

    public record Response(
            @NotNull @Size(min = 1, max = 4000) String question,
            @NotNull @Size(min = 1, max = 200) String topic,
            @NotNull Difficulty difficulty,
            @NotEmpty
            @Size(max = 20)
            List<@NotNull @Size(min = 1, max = 1000) String> expectedPoints,
            @NotNull QuestionType questionType,
            @NotNull @Valid Metadata metadata
    ) {
        public Response {
            expectedPoints = expectedPoints == null ? null : List.copyOf(expectedPoints);
        }

        @AssertTrue(message = "expectedPoints must contain unique values")
        @JsonIgnore
        public boolean isExpectedPointsUnique() {
            return expectedPoints == null
                    || expectedPoints.size() == new HashSet<>(expectedPoints).size();
        }
    }
}
