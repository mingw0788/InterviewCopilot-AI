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

public final class ReportGenerationModels {
    private ReportGenerationModels() {
    }

    public enum Difficulty { EASY, MEDIUM, HARD }

    public record Metrics(
            @NotNull @Min(0) @Max(100) BigDecimal interviewOverallScore,
            @Min(3) @Max(10) int questionCount,
            @Min(3) @Max(10) int completedQuestionCount,
            @NotNull @Min(0) @Max(100) BigDecimal averageAccuracy,
            @NotNull @Min(0) @Max(100) BigDecimal averageCompleteness,
            @NotNull @Min(0) @Max(100) BigDecimal averageDepth,
            @NotNull @Min(0) @Max(100) BigDecimal averageClarity,
            @Min(0) long durationSeconds
    ) {
    }

    public record EvaluationSummary(
            @Min(1) @Max(10) int questionNumber,
            @NotNull @Min(0) @Max(100) BigDecimal answerOverallScore,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1000) String> strengths,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 1000) String> missingPoints,
            @NotBlank @Size(max = 4000) String feedback
    ) {
    }

    public record Request(
            @NotBlank @Size(max = 100) String targetPosition,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 50) String> skills,
            @NotNull Difficulty difficulty,
            @NotNull @Valid Metrics metrics,
            @NotEmpty @Size(max = 10) List<@NotNull @Valid EvaluationSummary> evaluationSummaries,
            @NotBlank @Pattern(regexp = "zh-CN") String language
    ) {
        public Request {
            skills = skills == null ? null : List.copyOf(skills);
            evaluationSummaries = evaluationSummaries == null ? null : List.copyOf(evaluationSummaries);
        }
    }

    public record TokenUsage(
            @JsonProperty("input_tokens")
            @NotNull @Min(0) Integer inputTokens,
            @JsonProperty("output_tokens")
            @NotNull @Min(0) Integer outputTokens,
            @JsonProperty("total_tokens")
            @NotNull @Min(0) Integer totalTokens
    ) {
    }

    public record Metadata(
            @JsonProperty("llm_provider")
            @NotBlank @Size(max = 100) String llmProvider,
            @JsonProperty("model_name")
            @NotBlank @Size(max = 200) String modelName,
            @JsonProperty("prompt_version")
            @NotNull @Pattern(regexp = "report-v1") String promptVersion,
            @JsonProperty("token_usage")
            @NotNull @Valid TokenUsage tokenUsage,
            @JsonProperty("latency_ms")
            @NotNull @Min(0) Long latencyMs
    ) {
    }

    public record Response(
            @JsonProperty("strength_summary")
            @NotBlank @Size(max = 4000) String strengthSummary,
            @JsonProperty("weakness_summary")
            @NotBlank @Size(max = 4000) String weaknessSummary,
            @JsonProperty("improvement_suggestions")
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 1000) String> improvementSuggestions,
            @JsonProperty("overall_comment")
            @NotBlank @Size(max = 4000) String overallComment,
            @NotNull @Valid Metadata metadata
    ) {
        public Response {
            improvementSuggestions = improvementSuggestions == null ? null : List.copyOf(improvementSuggestions);
        }
    }
}
