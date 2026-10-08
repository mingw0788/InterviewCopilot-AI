package com.interviewcopilot.business.interview.domain;

import java.math.BigDecimal;

public record InterviewMetrics(
        BigDecimal interviewOverallScore,
        int questionCount,
        int completedQuestionCount,
        BigDecimal averageAccuracy,
        BigDecimal averageCompleteness,
        BigDecimal averageDepth,
        BigDecimal averageClarity,
        long durationSeconds
) {
    public InterviewMetrics {
        interviewOverallScore = ScoreCalculator.dimensionScore(
                interviewOverallScore, "interviewOverallScore");
        averageAccuracy = ScoreCalculator.dimensionScore(averageAccuracy, "averageAccuracy");
        averageCompleteness = ScoreCalculator.dimensionScore(averageCompleteness, "averageCompleteness");
        averageDepth = ScoreCalculator.dimensionScore(averageDepth, "averageDepth");
        averageClarity = ScoreCalculator.dimensionScore(averageClarity, "averageClarity");
        if (questionCount < InterviewConfiguration.MIN_QUESTION_COUNT
                || questionCount > InterviewConfiguration.MAX_QUESTION_COUNT) {
            throw new DomainValidationException("questionCount must be between 3 and 10");
        }
        if (completedQuestionCount < 0 || completedQuestionCount > questionCount) {
            throw new DomainValidationException("completedQuestionCount must be between 0 and questionCount");
        }
        if (durationSeconds < 0) {
            throw new DomainValidationException("durationSeconds must not be negative");
        }
    }
}
