package com.interviewcopilot.business.interview.application;

import com.interviewcopilot.business.idempotency.IdempotencyDecision;
import com.interviewcopilot.business.idempotency.IdempotencyOperation;
import com.interviewcopilot.business.idempotency.IdempotencyService;
import com.interviewcopilot.business.integration.ai.AiQuestionClient;
import com.interviewcopilot.business.integration.ai.AiEvaluationClient;
import com.interviewcopilot.business.integration.ai.AiReportClient;
import com.interviewcopilot.business.integration.ai.AnswerEvaluationModels;
import com.interviewcopilot.business.integration.ai.AiRequestContext;
import com.interviewcopilot.business.integration.ai.QuestionGenerationModels;
import com.interviewcopilot.business.integration.ai.ReportGenerationModels;
import com.interviewcopilot.business.interview.domain.Difficulty;
import com.interviewcopilot.business.interview.domain.AnswerEvaluation;
import com.interviewcopilot.business.interview.domain.AiCallMetadata;
import com.interviewcopilot.business.interview.domain.InterviewAnswer;
import com.interviewcopilot.business.interview.domain.InterviewQuestion;
import com.interviewcopilot.business.interview.domain.InterviewReport;
import com.interviewcopilot.business.interview.domain.InterviewConfiguration;
import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.domain.InterviewStatus;
import com.interviewcopilot.business.interview.repository.InterviewReportRepository;
import com.interviewcopilot.business.interview.repository.InterviewSessionPage;
import com.interviewcopilot.business.interview.repository.InterviewSessionRepository;
import com.interviewcopilot.business.web.ApiErrorCode;
import com.interviewcopilot.business.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewApplicationServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-08T01:00:00Z");

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewOwnershipService ownership = mock(InterviewOwnershipService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final AiQuestionClient aiQuestions = mock(AiQuestionClient.class);
    private final AiEvaluationClient aiEvaluations = mock(AiEvaluationClient.class);
    private final InterviewReportRepository reports = mock(InterviewReportRepository.class);
    private final AiReportClient aiReports = mock(AiReportClient.class);
    private final InterviewApplicationService service = new InterviewApplicationService(
            sessions, ownership, idempotency, aiQuestions, aiEvaluations, reports, aiReports,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void createsAnInterviewAndCompletesItsIdempotencyRecord() {
        UUID recordId = UUID.randomUUID();
        InterviewSession session = session(UUID.randomUUID());
        when(idempotency.begin(eq(userId), eq(IdempotencyOperation.CREATE_INTERVIEW), eq("key-123456"), any()))
                .thenReturn(IdempotencyDecision.proceed(recordId));
        when(sessions.save(any(InterviewSession.class))).thenReturn(session);

        InterviewApplicationService.CreateResult result = service.create(
                userId,
                new InterviewApplicationService.CreateCommand(
                        "Java Backend Engineer", List.of("Java", "Spring"), Difficulty.MEDIUM, null),
                "key-123456");

        assertSame(session, result.session());
        assertFalse(result.replay());
        verify(sessions).save(any(InterviewSession.class));
        verify(idempotency).complete(recordId, session.id());
        verify(idempotency, never()).abandon(recordId);
    }

    @Test
    void replaysTheOwnedResourceWithoutCreatingAnotherInterview() {
        UUID recordId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();
        InterviewSession session = session(interviewId);
        when(idempotency.begin(eq(userId), eq(IdempotencyOperation.CREATE_INTERVIEW), eq("key-123456"), any()))
                .thenReturn(IdempotencyDecision.replay(recordId, interviewId));
        when(ownership.requireOwned(interviewId, userId)).thenReturn(session);

        InterviewApplicationService.CreateResult result = service.create(
                userId,
                new InterviewApplicationService.CreateCommand(
                        "Java Backend Engineer", List.of("Java"), Difficulty.EASY, 3),
                "key-123456");

        assertSame(session, result.session());
        assertTrue(result.replay());
        verify(sessions, never()).save(any());
        verify(ownership).requireOwned(interviewId, userId);
    }

    @Test
    void convertsThePublicOneBasedPageToTheRepositoryZeroBasedPage() {
        InterviewSessionPage page = new InterviewSessionPage(List.of(), 2, 20, 42, 3);
        when(sessions.findPageByUserId(userId, Optional.of(InterviewStatus.CREATED), 2, 20)).thenReturn(page);

        assertSame(page, service.list(userId, Optional.of(InterviewStatus.CREATED), 3, 20));
        verify(sessions).findPageByUserId(userId, Optional.of(InterviewStatus.CREATED), 2, 20);
    }

    @Test
    void defaultsQuestionCountToFive() {
        when(idempotency.begin(any(), any(), any(), any()))
                .thenReturn(IdempotencyDecision.proceed(UUID.randomUUID()));
        when(sessions.save(any(InterviewSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InterviewApplicationService.CreateResult result = service.create(
                userId,
                new InterviewApplicationService.CreateCommand(
                        "Backend Engineer", List.of("Java"), Difficulty.MEDIUM, null),
                "key-123456");

        assertEquals(5, result.session().configuration().questionCount());
    }

    @Test
    void startsAnInterviewByGeneratingAndPersistingTheFirstQuestion() {
        UUID recordId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();
        InterviewSession session = session(interviewId);
        when(ownership.requireOwned(interviewId, userId)).thenReturn(session);
        when(idempotency.begin(eq(userId), eq(IdempotencyOperation.START_INTERVIEW), eq("start-1234"), any()))
                .thenReturn(IdempotencyDecision.proceed(recordId));
        when(aiQuestions.generateQuestion(any(), any())).thenReturn(questionResponse());
        when(sessions.save(session)).thenReturn(session);

        InterviewApplicationService.StartResult result = service.start(
                userId, interviewId, "start-1234", "request-123");

        assertFalse(result.replay());
        assertEquals(InterviewStatus.IN_PROGRESS, result.session().status());
        assertEquals(1, result.question().number());
        assertEquals(1, result.session().questions().size());
        verify(idempotency).complete(recordId, interviewId);
        verify(aiQuestions).generateQuestion(any(), any(AiRequestContext.class));
    }

    @Test
    void submitsAnswerEvaluatesItAndGeneratesTheNextQuestion() {
        UUID recordId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();
        InterviewSession interview = session(interviewId);
        interview.start(NOW);
        interview.addQuestion(InterviewQuestionFixture.question(1, NOW));
        when(ownership.requireOwned(interviewId, userId)).thenReturn(interview);
        when(idempotency.begin(eq(userId), eq(IdempotencyOperation.SUBMIT_ANSWER), eq("answer-1234"), any()))
                .thenReturn(IdempotencyDecision.proceed(recordId));
        when(aiEvaluations.evaluate(any(), any())).thenReturn(evaluationResponse());
        when(aiQuestions.generateQuestion(any(), any())).thenReturn(questionResponse());
        when(sessions.save(interview)).thenReturn(interview);

        InterviewApplicationService.SubmitResult result = service.submitAnswer(userId, interviewId,
                new InterviewApplicationService.SubmitCommand(interview.questions().getFirst().id(), "I would enforce a boundary."),
                "answer-1234", "request-123");

        assertFalse(result.replay());
        assertEquals(2, result.session().questions().size());
        assertTrue(result.session().questions().getFirst().isEvaluated());
        verify(idempotency).complete(recordId, interviewId);
        verify(aiEvaluations).evaluate(any(), any(AiRequestContext.class));
    }

    @Test
    void returnsTheOwnedSessionForCurrentQuestionQueries() {
        UUID interviewId = UUID.randomUUID();
        InterviewSession session = session(interviewId);
        session.start(NOW);
        session.addQuestion(InterviewQuestionFixture.question(1, NOW));
        when(ownership.requireOwned(interviewId, userId)).thenReturn(session);

        assertSame(session, service.currentQuestion(userId, interviewId));
        verify(ownership).requireOwned(interviewId, userId);
    }

    @Test
    void returnsConflictInsteadOfInternalErrorForQuestionsBeforeStartOrAfterCancellation() {
        UUID interviewId = UUID.randomUUID();
        InterviewSession interview = session(interviewId);
        when(ownership.requireOwned(interviewId, userId)).thenReturn(interview);
        assertEquals(ApiErrorCode.INTERVIEW_NOT_READY,
                assertThrows(ApiException.class, () -> service.currentQuestion(userId, interviewId)).code());
        interview.cancel(NOW);
        assertEquals(ApiErrorCode.ILLEGAL_INTERVIEW_STATE,
                assertThrows(ApiException.class, () -> service.currentQuestion(userId, interviewId)).code());
    }

    @Test
    void releasesClaimsWhenOwnershipChecksFailForStartCancelAndSubmit() {
        UUID interviewId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        when(idempotency.begin(any(), any(), any(), any())).thenReturn(IdempotencyDecision.proceed(recordId));
        when(ownership.requireOwned(interviewId, userId)).thenThrow(new ApiException(ApiErrorCode.FORBIDDEN, "Forbidden"));
        assertThrows(ApiException.class, () -> service.start(userId, interviewId, "start-1234", "request-123"));
        assertThrows(ApiException.class, () -> service.cancel(userId, interviewId, "cancel-1234"));
        assertThrows(ApiException.class, () -> service.submitAnswer(userId, interviewId,
                new InterviewApplicationService.SubmitCommand(UUID.randomUUID(), "answer"), "answer-1234", "request-123"));
        verify(idempotency, org.mockito.Mockito.times(3)).abandon(recordId);
    }

    @Test
    void rejectsAnswersBeforeStartAndReleasesTheClaim() {
        UUID interviewId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        when(ownership.requireOwned(interviewId, userId)).thenReturn(session(interviewId));
        when(idempotency.begin(any(), any(), any(), any())).thenReturn(IdempotencyDecision.proceed(recordId));
        assertEquals(ApiErrorCode.ILLEGAL_INTERVIEW_STATE, assertThrows(ApiException.class,
                () -> service.submitAnswer(userId, interviewId,
                        new InterviewApplicationService.SubmitCommand(UUID.randomUUID(), "answer"), "answer-1234", "request-123")).code());
        verify(idempotency).abandon(recordId);
        verify(aiEvaluations, never()).evaluate(any(), any());
    }

    @Test
    void replaysTheRequestedAnswerInsteadOfTheLastQuestion() {
        UUID interviewId = UUID.randomUUID();
        InterviewSession interview = completedSession(interviewId);
        InterviewQuestion first = interview.questions().getFirst();
        when(ownership.requireOwned(interviewId, userId)).thenReturn(interview);
        when(idempotency.begin(any(), any(), any(), any()))
                .thenReturn(IdempotencyDecision.replay(UUID.randomUUID(), interviewId));
        var result = service.submitAnswer(userId, interviewId,
                new InterviewApplicationService.SubmitCommand(first.id(), "A concrete answer"), "answer-1234", "request-123");
        assertTrue(result.replay());
        assertSame(first, result.answeredQuestion());
        verify(aiEvaluations, never()).evaluate(any(), any());
        verify(sessions, never()).save(any());
    }

    @Test
    void replaysStartAfterCompletionWithoutRequiringACurrentQuestion() {
        UUID interviewId = UUID.randomUUID();
        InterviewSession interview = completedSession(interviewId);
        when(ownership.requireOwned(interviewId, userId)).thenReturn(interview);
        when(idempotency.begin(any(), any(), any(), any()))
                .thenReturn(IdempotencyDecision.replay(UUID.randomUUID(), interviewId));
        var result = service.start(userId, interviewId, "start-1234", "request-123");
        assertTrue(result.replay());
        assertEquals(1, result.question().number());
        verify(aiQuestions, never()).generateQuestion(any(), any());
    }

    @Test
    void cancelsAnInterviewAndCompletesItsIdempotencyRecord() {
        UUID recordId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();
        InterviewSession interview = session(interviewId);
        when(idempotency.begin(eq(userId), eq(IdempotencyOperation.CANCEL_INTERVIEW), eq("cancel-1234"), any()))
                .thenReturn(IdempotencyDecision.proceed(recordId));
        when(ownership.requireOwned(interviewId, userId)).thenReturn(interview);
        when(sessions.save(interview)).thenReturn(interview);

        InterviewApplicationService.CancelResult result = service.cancel(userId, interviewId, "cancel-1234");

        assertFalse(result.replay());
        assertEquals(InterviewStatus.CANCELLED, result.session().status());
        assertEquals(NOW, result.session().cancelledAt().orElseThrow());
        verify(sessions).save(interview);
        verify(idempotency).complete(recordId, interviewId);
    }

    @Test
    void replaysCancellationWithoutSavingAgain() {
        UUID recordId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();
        InterviewSession interview = session(interviewId);
        interview.cancel(NOW);
        when(idempotency.begin(eq(userId), eq(IdempotencyOperation.CANCEL_INTERVIEW), eq("cancel-1234"), any()))
                .thenReturn(IdempotencyDecision.replay(recordId, interviewId));
        when(ownership.requireOwned(interviewId, userId)).thenReturn(interview);

        InterviewApplicationService.CancelResult result = service.cancel(userId, interviewId, "cancel-1234");

        assertTrue(result.replay());
        assertSame(interview, result.session());
        verify(sessions, never()).save(any());
    }

    @Test
    void createsPendingReportWhenReadingACompletedInterviewWithoutOne() {
        UUID interviewId = UUID.randomUUID();
        InterviewSession interview = completedSession(interviewId);
        when(ownership.requireOwned(interviewId, userId)).thenReturn(interview);
        when(reports.findBySessionId(interviewId)).thenReturn(Optional.empty());
        when(reports.save(any(InterviewReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReport report = service.getReport(userId, interviewId);

        assertEquals(interviewId, report.sessionId());
        assertEquals(com.interviewcopilot.business.interview.domain.ReportStatus.PENDING, report.status());
        verify(reports).save(any(InterviewReport.class));
    }

    @Test
    void retriesReportGenerationWithoutChangingDeterministicMetrics() {
        UUID recordId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();
        InterviewSession interview = completedSession(interviewId);
        InterviewReport pending = InterviewReport.pending(UUID.randomUUID(), interview, NOW);
        when(ownership.requireOwned(interviewId, userId)).thenReturn(interview);
        when(idempotency.begin(eq(userId), eq(IdempotencyOperation.RETRY_INTERVIEW_REPORT), eq("report-1234"), any()))
                .thenReturn(IdempotencyDecision.proceed(recordId));
        when(reports.findBySessionId(interviewId)).thenReturn(Optional.of(pending));
        when(reports.save(any(InterviewReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiReports.generateReport(any(), any())).thenReturn(reportResponse());

        InterviewApplicationService.ReportResult result = service.retryReport(userId, interviewId,
                "report-1234", "request-123");

        assertFalse(result.replay());
        assertEquals(com.interviewcopilot.business.interview.domain.ReportStatus.AVAILABLE, result.report().status());
        assertEquals(interview.metrics().orElseThrow(), result.report().metrics());
        verify(aiReports).generateReport(any(), any());
        verify(idempotency).complete(recordId, result.report().id());
    }

    private QuestionGenerationModels.Response questionResponse() {
        return new QuestionGenerationModels.Response(
                "Explain transaction isolation.",
                "Transactions",
                QuestionGenerationModels.Difficulty.MEDIUM,
                List.of("Isolation levels", "Anomalies"),
                QuestionGenerationModels.QuestionType.CONCEPTUAL,
                new QuestionGenerationModels.Metadata(
                        "mock", "deterministic-question-provider", "question-v1",
                        new QuestionGenerationModels.TokenUsage(10, 20, 30), 5L));
    }

    private AnswerEvaluationModels.Response evaluationResponse() {
        return new AnswerEvaluationModels.Response(new java.math.BigDecimal("80"), new java.math.BigDecimal("75"),
                new java.math.BigDecimal("70"), new java.math.BigDecimal("85"), List.of("Clear boundary"),
                List.of("More examples"), "Good answer.", new AnswerEvaluationModels.Metadata("mock", "evaluation-model",
                "evaluation-v1", "rubric-v1", new AnswerEvaluationModels.TokenUsage(10, 20, 30), 5L));
    }

    private ReportGenerationModels.Response reportResponse() {
        return new ReportGenerationModels.Response("Strong fundamentals", "Needs more depth",
                List.of("Practice design"), "Good result",
                new ReportGenerationModels.Metadata("mock", "report-model", "report-v1",
                        new ReportGenerationModels.TokenUsage(10, 20, 30), 5L));
    }

    private InterviewSession completedSession(UUID id) {
        InterviewSession interview = InterviewSession.create(id, userId,
                new InterviewConfiguration("Java Backend Engineer", List.of("Java"), Difficulty.MEDIUM, 3),
                NOW.minusSeconds(300));
        interview.start(NOW.minusSeconds(200));
        for (int number = 1; number <= 3; number++) {
            InterviewQuestion question = InterviewQuestionFixture.question(number, NOW.minusSeconds(200).plusSeconds(number));
            interview.addQuestion(question);
            InterviewAnswer answer = InterviewAnswer.create(UUID.randomUUID(), question.id(), "A concrete answer", NOW);
            interview.submitAnswer(question.id(), answer);
            interview.recordEvaluation(question.id(), AnswerEvaluation.create(UUID.randomUUID(), answer.id(),
                    java.math.BigDecimal.valueOf(80), java.math.BigDecimal.valueOf(75),
                    java.math.BigDecimal.valueOf(70), java.math.BigDecimal.valueOf(85),
                    List.of("Clear"), List.of("More detail"), "Good answer",
                    new AiCallMetadata("mock", "evaluation-model", "evaluation-v1", java.util.Map.of(), 1L),
                    "rubric-v1", NOW));
        }
        interview.complete(NOW);
        return interview;
    }

    private static final class InterviewQuestionFixture {
        private static com.interviewcopilot.business.interview.domain.InterviewQuestion question(int number, Instant createdAt) {
            return com.interviewcopilot.business.interview.domain.InterviewQuestion.create(UUID.randomUUID(), number,
                    "Explain transaction isolation.", "Transactions", Difficulty.MEDIUM, List.of("Isolation levels"),
                    com.interviewcopilot.business.interview.domain.QuestionType.CONCEPTUAL,
                    new com.interviewcopilot.business.interview.domain.AiCallMetadata("mock", "question-model", "question-v1", java.util.Map.of(), 1L), createdAt);
        }
    }

    private InterviewSession session(UUID id) {
        return InterviewSession.create(id, userId,
                new InterviewConfiguration("Java Backend Engineer", List.of("Java"), Difficulty.MEDIUM, 3), NOW);
    }
}
