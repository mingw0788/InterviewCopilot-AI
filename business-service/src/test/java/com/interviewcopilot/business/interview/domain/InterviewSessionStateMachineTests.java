package com.interviewcopilot.business.interview.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewSessionStateMachineTests {
    private static final Instant CREATED_AT = Instant.parse("2026-10-08T01:00:00Z");
    private static final Instant STARTED_AT = CREATED_AT.plusSeconds(60);

    @Test
    void startsOnlyFromCreated() {
        InterviewSession session = session();

        session.start(STARTED_AT);

        assertEquals(InterviewStatus.IN_PROGRESS, session.status());
        assertEquals(STARTED_AT, session.startedAt().orElseThrow());
        assertThrows(DomainRuleViolationException.class, () -> session.start(STARTED_AT));
    }

    @Test
    void cancellationIsLegalOnlyFromCreatedOrInProgress() {
        InterviewSession created = session();
        created.cancel(CREATED_AT.plusSeconds(1));
        assertEquals(InterviewStatus.CANCELLED, created.status());
        assertThrows(DomainRuleViolationException.class,
                () -> created.cancel(CREATED_AT.plusSeconds(2)));

        InterviewSession inProgress = session();
        inProgress.start(STARTED_AT);
        inProgress.cancel(STARTED_AT.plusSeconds(1));
        assertEquals(InterviewStatus.CANCELLED, inProgress.status());
        assertThrows(DomainRuleViolationException.class,
                () -> inProgress.markUnrecoverableFailure());
    }

    @Test
    void unrecoverableFailureIsLegalOnlyFromInProgressAndIsTerminal() {
        InterviewSession created = session();
        assertThrows(DomainRuleViolationException.class, created::markUnrecoverableFailure);

        InterviewSession failed = session();
        failed.start(STARTED_AT);
        failed.markUnrecoverableFailure();

        assertEquals(InterviewStatus.FAILED, failed.status());
        assertThrows(DomainRuleViolationException.class,
                () -> failed.cancel(STARTED_AT.plusSeconds(1)));
        assertThrows(DomainRuleViolationException.class,
                () -> failed.complete(STARTED_AT.plusSeconds(1)));
    }

    @Test
    void completionRequiresEveryConfiguredQuestionToBeEvaluatedAndIsTerminal() {
        InterviewSession session = session();
        session.start(STARTED_AT);

        assertThrows(DomainRuleViolationException.class,
                () -> session.complete(STARTED_AT.plusSeconds(60)));
        completeQuestion(session, 1);
        completeQuestion(session, 2);
        completeQuestion(session, 3);

        InterviewMetrics metrics = session.complete(STARTED_AT.plusSeconds(300));

        assertEquals(InterviewStatus.COMPLETED, session.status());
        assertEquals(3, metrics.completedQuestionCount());
        assertEquals(300, metrics.durationSeconds());
        assertTrue(session.interviewOverallScore().isPresent());
        assertThrows(DomainRuleViolationException.class,
                () -> session.cancel(STARTED_AT.plusSeconds(301)));
        assertThrows(DomainRuleViolationException.class,
                () -> session.addQuestion(question(4)));
        assertThrows(DomainRuleViolationException.class,
                () -> session.complete(STARTED_AT.plusSeconds(301)));
    }

    @Test
    void rejectsChronologicallyInvalidTransitions() {
        InterviewSession session = session();
        assertThrows(DomainValidationException.class,
                () -> session.start(CREATED_AT.minusSeconds(1)));
        assertEquals(InterviewStatus.CREATED, session.status());
        assertFalse(session.startedAt().isPresent());
    }

    @Test
    void everyTerminalStateRejectsEveryFurtherTransition() {
        InterviewSession completed = session();
        completed.start(STARTED_AT);
        completeQuestion(completed, 1);
        completeQuestion(completed, 2);
        completeQuestion(completed, 3);
        completed.complete(STARTED_AT.plusSeconds(300));

        InterviewSession cancelled = session();
        cancelled.cancel(CREATED_AT.plusSeconds(1));

        InterviewSession failed = session();
        failed.start(STARTED_AT);
        failed.markUnrecoverableFailure();

        for (InterviewSession terminal : List.of(completed, cancelled, failed)) {
            assertThrows(DomainRuleViolationException.class, () -> terminal.start(STARTED_AT));
            assertThrows(DomainRuleViolationException.class,
                    () -> terminal.cancel(STARTED_AT.plusSeconds(301)));
            assertThrows(DomainRuleViolationException.class, terminal::markUnrecoverableFailure);
            assertThrows(DomainRuleViolationException.class,
                    () -> terminal.complete(STARTED_AT.plusSeconds(301)));
        }
    }

    private InterviewSession session() {
        return InterviewSession.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new InterviewConfiguration("Java Engineer", List.of("Java"), Difficulty.MEDIUM, 3),
                CREATED_AT);
    }

    private void completeQuestion(InterviewSession session, int number) {
        InterviewQuestion question = question(number);
        session.addQuestion(question);
        InterviewAnswer answer = DomainFixtures.answer(question, number);
        session.submitAnswer(question.id(), answer);
        session.recordEvaluation(question.id(), DomainFixtures.evaluation(answer, number));
    }

    private InterviewQuestion question(int number) {
        return DomainFixtures.question(number, STARTED_AT.plusSeconds(number));
    }
}
