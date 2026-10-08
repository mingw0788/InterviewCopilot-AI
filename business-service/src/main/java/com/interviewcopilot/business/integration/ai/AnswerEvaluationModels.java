package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class AnswerEvaluationModels {
    private AnswerEvaluationModels() {}

    public record Question(
            @NotBlank @Size(max = 4000) String question,
            @NotBlank @Size(max = 200) String topic,
            @NotNull Difficulty difficulty,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 1000) String> expectedPoints,
            @NotNull QuestionType questionType
    ) {}

    public record Request(
            @NotBlank @Size(max = 100) String targetPosition,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 50) String> skills,
            @NotNull Difficulty difficulty,
            @NotNull @Valid Question question,
            @NotBlank @Size(max = 8000) String candidateAnswer,
            @NotNull @Valid ScoringRubric scoringRubric,
            @NotBlank @Pattern(regexp = "zh-CN") String language
    ) {}

    public record ScoringRubric(
            @NotBlank @Size(max = 1000) String accuracy,
            @NotBlank @Size(max = 1000) String completeness,
            @NotBlank @Size(max = 1000) String depth,
            @NotBlank @Size(max = 1000) String clarity
    ) {}

    public record Metadata(
            @JsonProperty("llm_provider")
            @NotBlank @Size(max = 100) String llmProvider,
            @JsonProperty("model_name")
            @NotBlank @Size(max = 200) String modelName,
            @JsonProperty("prompt_version")
            @NotBlank @Pattern(regexp = "evaluation-v1") String promptVersion,
            @JsonProperty("evaluation_version")
            @NotBlank @Size(max = 100) String evaluationVersion,
            @JsonProperty("token_usage")
            @NotNull @Valid TokenUsage tokenUsage,
            @JsonProperty("latency_ms")
            @NotNull @Min(0) Long latencyMs
    ) {}

    public record TokenUsage(@JsonProperty("input_tokens") @NotNull @Min(0) Integer inputTokens,
                             @JsonProperty("output_tokens") @NotNull @Min(0) Integer outputTokens,
                             @JsonProperty("total_tokens") @NotNull @Min(0) Integer totalTokens) {}

    public record Response(
            @NotNull @Min(0) @Max(100) BigDecimal accuracy,
            @NotNull @Min(0) @Max(100) BigDecimal completeness,
            @NotNull @Min(0) @Max(100) BigDecimal depth,
            @NotNull @Min(0) @Max(100) BigDecimal clarity,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1000) String> strengths,
            @JsonProperty("missing_points")
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1000) String> missingPoints,
            @NotBlank @Size(max = 4000) String feedback,
            @NotNull @Valid Metadata metadata
    ) {}

    public enum Difficulty { EASY, MEDIUM, HARD }
    public enum QuestionType { CONCEPTUAL, PRACTICAL, SCENARIO, DESIGN }
}
