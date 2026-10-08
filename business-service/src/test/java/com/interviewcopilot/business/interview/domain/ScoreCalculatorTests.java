package com.interviewcopilot.business.interview.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreCalculatorTests {
    @Test
    void calculatesFrozenWeightedAnswerScore() {
        BigDecimal score = ScoreCalculator.answerOverallScore(
                new BigDecimal("85.00"),
                new BigDecimal("80.00"),
                new BigDecimal("78.00"),
                new BigDecimal("88.00"));

        assertEquals(new BigDecimal("82.80"), score);
    }

    @Test
    void roundsScoresHalfUpToTwoDecimalPlaces() {
        assertEquals(new BigDecimal("0.01"), ScoreCalculator.average(List.of(
                new BigDecimal("0.00"), new BigDecimal("0.01"))));
        assertEquals(new BigDecimal("66.67"), ScoreCalculator.average(List.of(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("0.00"))));
    }

    @Test
    void rejectsInvalidDimensionScoresAndEmptyAverage() {
        assertThrows(DomainValidationException.class, () -> ScoreCalculator.answerOverallScore(
                new BigDecimal("-0.01"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(DomainValidationException.class, () -> ScoreCalculator.answerOverallScore(
                new BigDecimal("100.01"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(DomainValidationException.class, () -> ScoreCalculator.answerOverallScore(
                new BigDecimal("1.001"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(DomainValidationException.class, () -> ScoreCalculator.average(List.of()));
    }

    @Test
    void rejectsAnExternallySuppliedOverallScoreThatDoesNotMatchTheFrozenFormula() {
        assertThrows(DomainValidationException.class, () -> new AnswerEvaluation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("85.00"),
                new BigDecimal("80.00"),
                new BigDecimal("78.00"),
                new BigDecimal("88.00"),
                new BigDecimal("100.00"),
                List.of("Strength"),
                List.of("Missing point"),
                "Feedback",
                DomainFixtures.metadata("evaluation-v1"),
                "rubric-v1",
                Instant.parse("2026-10-08T00:00:00Z")));
    }
}
