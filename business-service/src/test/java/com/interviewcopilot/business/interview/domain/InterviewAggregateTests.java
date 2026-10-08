package com.interviewcopilot.business.interview.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewAggregateTests {
    private static final Instant CREATED_AT = Instant.parse("2026-10-08T02:00:00Z");
    private InterviewSession session;

    @BeforeEach
    void setUp() {
        session = InterviewSession.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new InterviewConfiguration("Java Engineer", List.of("Java"), Difficulty.MEDIUM, 3),
                CREATED_AT);
    }

    @Test
    void enforcesSequentialQuestionsAndRequiresEvaluationBeforeNextQuestion() {
        assertThrows(DomainRuleViolationException.class,
                () -> session.addQuestion(DomainFixtures.question(1, CREATED_AT.plusSeconds(1))));
        session.start(CREATED_AT.plusSeconds(1));
        assertThrows(DomainRuleViolationException.class,
                () -> session.addQuestion(DomainFixtures.question(2, CREATED_AT.plusSeconds(2))));

        InterviewQuestion first = DomainFixtures.question(1, CREATED_AT.plusSeconds(2));
        session.addQuestion(first);
        assertThrows(DomainRuleViolationException.class,
                () -> session.addQuestion(DomainFixtures.question(2, CREATED_AT.plusSeconds(3))));

        InterviewAnswer answer = DomainFixtures.answer(first, 1);
        session.submitAnswer(first.id(), answer);
        assertThrows(DomainRuleViolationException.class,
                () -> session.addQuestion(DomainFixtures.question(2, CREATED_AT.plusSeconds(4))));

        session.recordEvaluation(first.id(), DomainFixtures.evaluation(answer, 1));
        session.addQuestion(DomainFixtures.question(2, CREATED_AT.plusSeconds(5)));

        assertEquals(2, session.currentQuestionNumber().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> session.questions().clear());
    }

    @Test
    void enforcesOneFormalAnswerAndOneEvaluation() {
        session.start(CREATED_AT.plusSeconds(1));
        InterviewQuestion question = DomainFixtures.question(1, CREATED_AT.plusSeconds(2));
        session.addQuestion(question);
        InterviewAnswer answer = DomainFixtures.answer(question, 1);

        session.submitAnswer(question.id(), answer);
        assertThrows(DomainRuleViolationException.class,
                () -> session.submitAnswer(question.id(), DomainFixtures.answer(question, 2)));
        AnswerEvaluation evaluation = DomainFixtures.evaluation(answer, 1);
        session.recordEvaluation(question.id(), evaluation);
        assertThrows(DomainRuleViolationException.class,
                () -> session.recordEvaluation(question.id(), DomainFixtures.evaluation(answer, 2)));

        assertEquals(evaluation.id(), question.answer().orElseThrow().evaluation().orElseThrow().id());
    }

    @Test
    void rejectsCrossEntityIdentifiersAndOperationsWithoutCurrentQuestion() {
        session.start(CREATED_AT.plusSeconds(1));
        InterviewAnswer unrelated = InterviewAnswer.create(
                UUID.randomUUID(), UUID.randomUUID(), "answer", CREATED_AT.plusSeconds(3));
        assertThrows(DomainRuleViolationException.class,
                () -> session.submitAnswer(unrelated.questionId(), unrelated));

        InterviewQuestion question = DomainFixtures.question(1, CREATED_AT.plusSeconds(2));
        session.addQuestion(question);
        assertThrows(DomainRuleViolationException.class,
                () -> session.submitAnswer(UUID.randomUUID(), DomainFixtures.answer(question, 1)));
        assertThrows(DomainRuleViolationException.class,
                () -> session.submitAnswer(question.id(), unrelated));
        assertThrows(DomainRuleViolationException.class,
                () -> session.recordEvaluation(question.id(), DomainFixtures.evaluation(unrelated, 1)));
    }

    @Test
    void computesDeterministicInterviewMetricsFromValidEvaluations() {
        session.start(CREATED_AT.plusSeconds(1));
        addEvaluatedQuestion(1, "100.00", "80.00", "60.00", "40.00");
        addEvaluatedQuestion(2, "50.00", "50.00", "50.00", "50.00");
        addEvaluatedQuestion(3, "0.00", "20.00", "40.00", "60.00");

        InterviewMetrics metrics = session.complete(CREATED_AT.plusSeconds(301));

        assertEquals(new BigDecimal("50.00"), metrics.averageAccuracy());
        assertEquals(new BigDecimal("50.00"), metrics.averageCompleteness());
        assertEquals(new BigDecimal("50.00"), metrics.averageDepth());
        assertEquals(new BigDecimal("50.00"), metrics.averageClarity());
        assertEquals(new BigDecimal("50.00"), metrics.interviewOverallScore());
        assertEquals(metrics.interviewOverallScore(), session.interviewOverallScore().orElseThrow());
        assertTrue(session.metrics().isPresent());
    }

    @Test
    void rejectsQuestionDifficultyDriftAndQuestionCountOverflow() {
        session.start(CREATED_AT.plusSeconds(1));
        InterviewQuestion wrongDifficulty = InterviewQuestion.create(
                UUID.randomUUID(), 1, "Question", "Topic", Difficulty.HARD,
                List.of("Point"), QuestionType.CONCEPTUAL, DomainFixtures.metadata("question-v1"),
                CREATED_AT.plusSeconds(2));
        assertThrows(DomainRuleViolationException.class, () -> session.addQuestion(wrongDifficulty));

        for (int number = 1; number <= 3; number++) {
            InterviewQuestion question = DomainFixtures.question(number, CREATED_AT.plusSeconds(number + 2));
            session.addQuestion(question);
            InterviewAnswer answer = DomainFixtures.answer(question, number);
            session.submitAnswer(question.id(), answer);
            session.recordEvaluation(question.id(), DomainFixtures.evaluation(answer, number));
        }
        assertThrows(DomainRuleViolationException.class,
                () -> session.addQuestion(DomainFixtures.question(4, CREATED_AT.plusSeconds(10))));
    }

    @Test
    void rejectsBackwardEventTimesWithoutPartiallyMutatingTheAggregate() {
        Instant startedAt = CREATED_AT.plusSeconds(10);
        session.start(startedAt);
        assertThrows(DomainValidationException.class,
                () -> session.addQuestion(DomainFixtures.question(1, startedAt.minusSeconds(1))));
        assertTrue(session.questions().isEmpty());

        InterviewQuestion question = DomainFixtures.question(1, startedAt.plusSeconds(1));
        session.addQuestion(question);
        InterviewAnswer earlyAnswer = InterviewAnswer.create(
                UUID.randomUUID(), question.id(), "Answer", question.createdAt().minusSeconds(1));
        assertThrows(DomainValidationException.class,
                () -> session.submitAnswer(question.id(), earlyAnswer));
        assertTrue(question.answer().isEmpty());

        InterviewAnswer answer = DomainFixtures.answer(question, 1);
        session.submitAnswer(question.id(), answer);
        AnswerEvaluation earlyEvaluation = AnswerEvaluation.create(
                UUID.randomUUID(), answer.id(),
                new BigDecimal("80.00"), new BigDecimal("80.00"),
                new BigDecimal("80.00"), new BigDecimal("80.00"),
                List.of(), List.of(), "Feedback",
                DomainFixtures.metadata("evaluation-v1"), "rubric-v1",
                answer.submittedAt().minusSeconds(1));
        assertThrows(DomainValidationException.class,
                () -> session.recordEvaluation(question.id(), earlyEvaluation));
        assertTrue(answer.evaluation().isEmpty());
    }

    private void addEvaluatedQuestion(
            int number,
            String accuracy,
            String completeness,
            String depth,
            String clarity
    ) {
        InterviewQuestion question = DomainFixtures.question(number, CREATED_AT.plusSeconds(number + 1));
        session.addQuestion(question);
        InterviewAnswer answer = DomainFixtures.answer(question, number);
        session.submitAnswer(question.id(), answer);
        session.recordEvaluation(question.id(), AnswerEvaluation.create(
                UUID.randomUUID(), answer.id(),
                new BigDecimal(accuracy), new BigDecimal(completeness),
                new BigDecimal(depth), new BigDecimal(clarity),
                List.of("Strength"), List.of("Missing point"), "Feedback",
                DomainFixtures.metadata("evaluation-v1"), "rubric-v1",
                answer.submittedAt().plusSeconds(1)));
    }
}
