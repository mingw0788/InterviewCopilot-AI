package com.interviewcopilot.business.interview.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnswerEvaluation(
        UUID id,
        UUID answerId,
        BigDecimal accuracy,
        BigDecimal completeness,
        BigDecimal depth,
        BigDecimal clarity,
        BigDecimal answerOverallScore,
        List<String> strengths,
        List<String> missingPoints,
        String feedback,
        AiCallMetadata evaluationMetadata,
        String evaluationVersion,
        Instant createdAt
) {
    public static AnswerEvaluation create(
            UUID id,
            UUID answerId,
            BigDecimal accuracy,
            BigDecimal completeness,
            BigDecimal depth,
            BigDecimal clarity,
            List<String> strengths,
            List<String> missingPoints,
            String feedback,
            AiCallMetadata evaluationMetadata,
            String evaluationVersion,
            Instant createdAt
    ) {
        BigDecimal normalizedAccuracy = ScoreCalculator.dimensionScore(accuracy, "accuracy");
        BigDecimal normalizedCompleteness = ScoreCalculator.dimensionScore(completeness, "completeness");
        BigDecimal normalizedDepth = ScoreCalculator.dimensionScore(depth, "depth");
        BigDecimal normalizedClarity = ScoreCalculator.dimensionScore(clarity, "clarity");
        BigDecimal overallScore = ScoreCalculator.answerOverallScore(
                normalizedAccuracy, normalizedCompleteness, normalizedDepth, normalizedClarity);

        return new AnswerEvaluation(
                id,
                answerId,
                normalizedAccuracy,
                normalizedCompleteness,
                normalizedDepth,
                normalizedClarity,
                overallScore,
                strengths,
                missingPoints,
                feedback,
                evaluationMetadata,
                evaluationVersion,
                createdAt);
    }

    public AnswerEvaluation {
        id = DomainChecks.required(id, "id");
        answerId = DomainChecks.required(answerId, "answerId");
        accuracy = ScoreCalculator.dimensionScore(accuracy, "accuracy");
        completeness = ScoreCalculator.dimensionScore(completeness, "completeness");
        depth = ScoreCalculator.dimensionScore(depth, "depth");
        clarity = ScoreCalculator.dimensionScore(clarity, "clarity");
        BigDecimal calculatedScore = ScoreCalculator.answerOverallScore(accuracy, completeness, depth, clarity);
        if (answerOverallScore == null || calculatedScore.compareTo(answerOverallScore) != 0) {
            throw new DomainValidationException("answerOverallScore must be calculated from dimension scores");
        }
        answerOverallScore = calculatedScore;
        strengths = DomainChecks.textList(strengths, "strengths", 0, 20, 1000);
        missingPoints = DomainChecks.textList(missingPoints, "missingPoints", 0, 20, 1000);
        feedback = DomainChecks.requiredText(feedback, "feedback", 4000);
        evaluationMetadata = DomainChecks.required(evaluationMetadata, "evaluationMetadata");
        evaluationVersion = DomainChecks.requiredText(evaluationVersion, "evaluationVersion", 40);
        createdAt = DomainChecks.required(createdAt, "createdAt");
    }
}
