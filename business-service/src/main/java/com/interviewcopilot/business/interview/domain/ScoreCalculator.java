package com.interviewcopilot.business.interview.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

public final class ScoreCalculator {
    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final BigDecimal ACCURACY_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal COMPLETENESS_WEIGHT = new BigDecimal("0.25");
    private static final BigDecimal DEPTH_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal CLARITY_WEIGHT = new BigDecimal("0.15");
    private static final int SCALE = 2;

    private ScoreCalculator() {
    }

    public static BigDecimal answerOverallScore(
            BigDecimal accuracy,
            BigDecimal completeness,
            BigDecimal depth,
            BigDecimal clarity
    ) {
        BigDecimal normalizedAccuracy = dimensionScore(accuracy, "accuracy");
        BigDecimal normalizedCompleteness = dimensionScore(completeness, "completeness");
        BigDecimal normalizedDepth = dimensionScore(depth, "depth");
        BigDecimal normalizedClarity = dimensionScore(clarity, "clarity");

        return normalizedAccuracy.multiply(ACCURACY_WEIGHT)
                .add(normalizedCompleteness.multiply(COMPLETENESS_WEIGHT))
                .add(normalizedDepth.multiply(DEPTH_WEIGHT))
                .add(normalizedClarity.multiply(CLARITY_WEIGHT))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal average(Collection<BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            throw new DomainValidationException("scores must not be empty");
        }
        BigDecimal total = BigDecimal.ZERO;
        int index = 0;
        for (BigDecimal score : scores) {
            total = total.add(dimensionScore(score, "scores[" + index + "]"));
            index++;
        }
        return total.divide(BigDecimal.valueOf(scores.size()), SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal dimensionScore(BigDecimal score, String field) {
        if (score == null) {
            throw new DomainValidationException(field + " is required");
        }
        final BigDecimal normalized;
        try {
            normalized = score.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new DomainValidationException(field + " must have at most two decimal places");
        }
        if (normalized.compareTo(MIN_SCORE) < 0 || normalized.compareTo(MAX_SCORE) > 0) {
            throw new DomainValidationException(field + " must be between 0 and 100");
        }
        return normalized;
    }
}
