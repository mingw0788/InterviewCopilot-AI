package com.interviewcopilot.business.interview.domain;

import java.util.List;

public record InterviewConfiguration(
        String targetPosition,
        List<String> skills,
        Difficulty difficulty,
        int questionCount
) {
    public static final int MIN_QUESTION_COUNT = 3;
    public static final int MAX_QUESTION_COUNT = 10;

    public InterviewConfiguration {
        targetPosition = DomainChecks.requiredText(targetPosition, "targetPosition", 100);
        skills = DomainChecks.caseInsensitiveDistinctTextList(skills, "skills", 1, 20, 50);
        difficulty = DomainChecks.required(difficulty, "difficulty");
        if (questionCount < MIN_QUESTION_COUNT || questionCount > MAX_QUESTION_COUNT) {
            throw new DomainValidationException("questionCount must be between 3 and 10");
        }
    }
}
