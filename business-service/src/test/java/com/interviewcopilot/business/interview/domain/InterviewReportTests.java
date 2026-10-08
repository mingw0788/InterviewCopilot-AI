package com.interviewcopilot.business.interview.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewReportTests {
    private static final Instant CREATED_AT = Instant.parse("2026-10-08T03:00:00Z");

    @Test
    void managesIndependentRetryableReportLifecycleWithoutChangingMetrics() {
        InterviewSession session = completedSession();
        InterviewMetrics metrics = session.metrics().orElseThrow();
        InterviewReport report = InterviewReport.pending(UUID.randomUUID(), session, CREATED_AT);

        assertEquals(ReportStatus.PENDING, report.status());
        assertEquals(session.id(), report.sessionId());
        assertFalse(report.summary().isPresent());
        report.beginGeneration();
        report.markGenerationFailed();
        report.beginGeneration();
        report.publish(new ReportSummary(
                "Strong fundamentals", "Needs more depth", List.of("Practice design"), "Good result"),
                DomainFixtures.metadata("report-v1"),
                "report-v1",
                CREATED_AT.plusSeconds(30));

        assertEquals(ReportStatus.AVAILABLE, report.status());
        assertEquals(metrics, report.metrics());
        assertTrue(report.summary().isPresent());
        assertTrue(report.publishedAt().isPresent());
        assertThrows(DomainRuleViolationException.class, report::beginGeneration);
        assertThrows(DomainRuleViolationException.class, report::markGenerationFailed);
    }

    @Test
    void rejectsIllegalReportTransitionsAndNonCompletedInterviews() {
        InterviewReport report = InterviewReport.pending(UUID.randomUUID(), completedSession(), CREATED_AT);
        ReportSummary summary = new ReportSummary(
                "Strength", "Weakness", List.of("Suggestion"), "Comment");

        assertThrows(DomainRuleViolationException.class,
                () -> report.publish(
                        summary, DomainFixtures.metadata("report-v1"), "report-v1",
                        CREATED_AT.plusSeconds(1)));
        assertThrows(DomainRuleViolationException.class, report::markGenerationFailed);
        report.beginGeneration();
        assertThrows(DomainValidationException.class,
                () -> report.publish(
                        summary, DomainFixtures.metadata("report-v1"), "report-v1",
                        CREATED_AT.minusSeconds(1)));
        assertEquals(ReportStatus.GENERATING, report.status());
        assertFalse(report.summary().isPresent());
        assertFalse(report.publishedAt().isPresent());

        InterviewSession incomplete = InterviewSession.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new InterviewConfiguration("Java Engineer", List.of("Java"), Difficulty.MEDIUM, 3),
                CREATED_AT.minusSeconds(400));
        assertThrows(DomainRuleViolationException.class,
                () -> InterviewReport.pending(UUID.randomUUID(), incomplete, CREATED_AT));
        assertThrows(DomainValidationException.class,
                () -> InterviewReport.pending(
                        UUID.randomUUID(), completedSession(), CREATED_AT.minusSeconds(2)));
    }

    private InterviewSession completedSession() {
        Instant sessionCreatedAt = CREATED_AT.minusSeconds(400);
        Instant startedAt = CREATED_AT.minusSeconds(300);
        InterviewSession session = InterviewSession.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new InterviewConfiguration("Java Engineer", List.of("Java"), Difficulty.MEDIUM, 3),
                sessionCreatedAt);
        session.start(startedAt);
        for (int number = 1; number <= 3; number++) {
            InterviewQuestion question = DomainFixtures.question(number, startedAt.plusSeconds(number));
            session.addQuestion(question);
            InterviewAnswer answer = DomainFixtures.answer(question, number);
            session.submitAnswer(question.id(), answer);
            session.recordEvaluation(question.id(), DomainFixtures.evaluation(answer, number));
        }
        session.complete(CREATED_AT.minusSeconds(1));
        return session;
    }
}
