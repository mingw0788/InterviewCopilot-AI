package com.interviewcopilot.business.interview.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewConfigurationTests {
    @Test
    void normalizesTextAndDeduplicatesSkillsCaseInsensitively() {
        List<String> mutableSkills = new ArrayList<>(List.of(" Java ", "Spring Boot", "java"));

        InterviewConfiguration configuration = new InterviewConfiguration(
                " Java Backend Engineer ", mutableSkills, Difficulty.MEDIUM, 5);
        mutableSkills.add("MySQL");

        assertEquals("Java Backend Engineer", configuration.targetPosition());
        assertEquals(List.of("Java", "Spring Boot"), configuration.skills());
        assertThrows(UnsupportedOperationException.class, () -> configuration.skills().add("MySQL"));
    }

    @Test
    void acceptsFrozenQuestionCountBoundaries() {
        assertEquals(3, configuration(3).questionCount());
        assertEquals(10, configuration(10).questionCount());
    }

    @Test
    void appliesTextLimitsByUnicodeCharactersRatherThanUtf16Units() {
        String oneHundredCharacters = "😀".repeat(100);

        InterviewConfiguration configuration = new InterviewConfiguration(
                oneHundredCharacters, List.of("Java"), Difficulty.MEDIUM, 5);

        assertEquals(oneHundredCharacters, configuration.targetPosition());
        assertThrows(DomainValidationException.class, () -> new InterviewConfiguration(
                "😀".repeat(101), List.of("Java"), Difficulty.MEDIUM, 5));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(DomainValidationException.class,
                () -> new InterviewConfiguration(" ", List.of("Java"), Difficulty.MEDIUM, 5));
        assertThrows(DomainValidationException.class,
                () -> new InterviewConfiguration("Engineer", List.of(), Difficulty.MEDIUM, 5));
        assertThrows(DomainValidationException.class,
                () -> new InterviewConfiguration("Engineer", List.of(" "), Difficulty.MEDIUM, 5));
        assertThrows(DomainValidationException.class,
                () -> new InterviewConfiguration("Engineer", List.of("Java"), null, 5));
        assertThrows(DomainValidationException.class, () -> configuration(2));
        assertThrows(DomainValidationException.class, () -> configuration(11));
    }

    private InterviewConfiguration configuration(int questionCount) {
        return new InterviewConfiguration("Engineer", List.of("Java"), Difficulty.MEDIUM, questionCount);
    }
}
