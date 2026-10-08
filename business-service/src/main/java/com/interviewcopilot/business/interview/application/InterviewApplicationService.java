package com.interviewcopilot.business.interview.application;

import com.interviewcopilot.business.idempotency.IdempotencyDecision;
import com.interviewcopilot.business.idempotency.IdempotencyOperation;
import com.interviewcopilot.business.idempotency.IdempotencyService;
import com.interviewcopilot.business.integration.ai.AiQuestionClient;
import com.interviewcopilot.business.integration.ai.AiClientException;
import com.interviewcopilot.business.integration.ai.AiEvaluationClient;
import com.interviewcopilot.business.integration.ai.AnswerEvaluationModels;
import com.interviewcopilot.business.integration.ai.AiRequestContext;
import com.interviewcopilot.business.integration.ai.AiReportClient;
import com.interviewcopilot.business.integration.ai.ReportGenerationModels;
import com.interviewcopilot.business.integration.ai.QuestionGenerationModels;
import com.interviewcopilot.business.interview.domain.AiCallMetadata;
import com.interviewcopilot.business.interview.domain.Difficulty;
import com.interviewcopilot.business.interview.domain.InterviewConfiguration;
import com.interviewcopilot.business.interview.domain.InterviewQuestion;
import com.interviewcopilot.business.interview.domain.InterviewAnswer;
import com.interviewcopilot.business.interview.domain.AnswerEvaluation;
import com.interviewcopilot.business.interview.domain.InterviewMetrics;
import com.interviewcopilot.business.interview.domain.QuestionType;
import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.domain.InterviewStatus;
import com.interviewcopilot.business.interview.domain.InterviewReport;
import com.interviewcopilot.business.interview.domain.ReportSummary;
import com.interviewcopilot.business.interview.repository.InterviewReportRepository;
import com.interviewcopilot.business.interview.repository.InterviewSessionPage;
import com.interviewcopilot.business.interview.repository.InterviewSessionRepository;
import com.interviewcopilot.business.web.ApiErrorCode;
import com.interviewcopilot.business.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InterviewApplicationService {
    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int MAX_PAGE_SIZE = 50;

    private final InterviewSessionRepository sessions;
    private final InterviewOwnershipService ownership;
    private final IdempotencyService idempotency;
    private final AiQuestionClient aiQuestions;
    private final AiEvaluationClient aiEvaluations;
    private final InterviewReportRepository reports;
    private final AiReportClient aiReports;
    private final Clock clock;

    public InterviewApplicationService(
            InterviewSessionRepository sessions,
            InterviewOwnershipService ownership,
            IdempotencyService idempotency,
            AiQuestionClient aiQuestions,
            AiEvaluationClient aiEvaluations,
            InterviewReportRepository reports,
            AiReportClient aiReports,
            Clock clock
    ) {
        this.sessions = sessions;
        this.ownership = ownership;
        this.idempotency = idempotency;
        this.aiQuestions = aiQuestions;
        this.aiEvaluations = aiEvaluations;
        this.reports = reports;
        this.aiReports = aiReports;
        this.clock = clock;
    }

    @Transactional
    public SubmitResult submitAnswer(UUID userId, UUID interviewId, SubmitCommand command, String idempotencyKey, String requestId) {
        IdempotencyDecision decision = idempotency.begin(userId, IdempotencyOperation.SUBMIT_ANSWER, idempotencyKey,
                new SubmitIdentity(interviewId, command.questionId(), command.answerContent()));
        if (decision.type() == IdempotencyDecision.Type.REPLAY) {
            InterviewSession session = ownership.requireOwned(interviewId, userId);
            InterviewQuestion answered = session.questions().stream()
                    .filter(question -> question.id().equals(command.questionId()))
                    .findFirst().orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "Question not found"));
            return new SubmitResult(session, answered, true);
        }
        try {
            InterviewSession session = ownership.requireOwned(interviewId, userId);
            if (session.status() != InterviewStatus.IN_PROGRESS) {
                throw new ApiException(ApiErrorCode.ILLEGAL_INTERVIEW_STATE, "Interview is not in progress");
            }
            InterviewQuestion question = session.currentQuestion()
                    .orElseThrow(() -> new ApiException(ApiErrorCode.INTERVIEW_NOT_READY, "No current question is available"));
            Instant submittedAt = clock.instant();
            InterviewAnswer answer = InterviewAnswer.create(UUID.randomUUID(), command.questionId(), command.answerContent(), submittedAt);
            session.submitAnswer(command.questionId(), answer);
            AnswerEvaluationModels.Response evaluated = aiEvaluations.evaluate(toEvaluationRequest(session, question, command.answerContent()),
                    new AiRequestContext(requestId, UUID.randomUUID().toString(), idempotencyKey));
            AnswerEvaluation evaluation = toEvaluation(evaluated, answer.id(), submittedAt);
            session.recordEvaluation(command.questionId(), evaluation);
            if (session.questions().size() == session.configuration().questionCount()) session.complete(clock.instant());
            else {
                QuestionGenerationModels.Response generated = aiQuestions.generateQuestion(new QuestionGenerationModels.Request(
                        session.configuration().targetPosition(), session.configuration().skills(), toAiDifficulty(session.configuration().difficulty()),
                        session.questions().size() + 1, session.configuration().questionCount(),
                        session.questions().stream().map(q -> new QuestionGenerationModels.PreviousQuestion(q.number(), q.text(), q.topic(),
                                QuestionGenerationModels.QuestionType.valueOf(q.type().name()))).toList(), "zh-CN"),
                        new AiRequestContext(requestId, UUID.randomUUID().toString(), idempotencyKey));
                session.addQuestion(toQuestion(generated, session, clock.instant(), session.questions().size() + 1));
            }
            InterviewSession saved = sessions.save(session);
            if (saved.status() == InterviewStatus.COMPLETED) {
                ensurePendingReport(saved);
            }
            idempotency.complete(decision.recordId(), saved.id());
            return new SubmitResult(saved, saved.questions().stream()
                    .filter(item -> item.id().equals(command.questionId())).findFirst().orElseThrow(), false);
        } catch (RuntimeException exception) { idempotency.abandon(decision.recordId()); throw exception; }
    }

    private AnswerEvaluationModels.Request toEvaluationRequest(InterviewSession session, InterviewQuestion q, String answer) {
        return new AnswerEvaluationModels.Request(session.configuration().targetPosition(), session.configuration().skills(),
                AnswerEvaluationModels.Difficulty.valueOf(session.configuration().difficulty().name()),
                new AnswerEvaluationModels.Question(q.text(), q.topic(), AnswerEvaluationModels.Difficulty.valueOf(q.difficulty().name()), q.expectedPoints(),
                        AnswerEvaluationModels.QuestionType.valueOf(q.type().name())), answer,
                new AnswerEvaluationModels.ScoringRubric("Technical correctness", "Coverage of expected points", "Depth and boundaries", "Structure and readability"), "zh-CN");
    }

    private AnswerEvaluation toEvaluation(AnswerEvaluationModels.Response r, UUID answerId, Instant createdAt) {
        var m = r.metadata();
        return AnswerEvaluation.create(UUID.randomUUID(), answerId, r.accuracy(), r.completeness(), r.depth(), r.clarity(), r.strengths(), r.missingPoints(), r.feedback(),
                new AiCallMetadata(m.llmProvider(), m.modelName(), m.promptVersion(), java.util.Map.of("input_tokens", m.tokenUsage().inputTokens().longValue(), "output_tokens", m.tokenUsage().outputTokens().longValue(), "total_tokens", m.tokenUsage().totalTokens().longValue()), m.latencyMs()), m.evaluationVersion(), createdAt);
    }

    @Transactional
    public CreateResult create(UUID userId, CreateCommand command, String idempotencyKey) {
        IdempotencyDecision decision = idempotency.begin(
                userId, IdempotencyOperation.CREATE_INTERVIEW, idempotencyKey, command);
        if (decision.type() == IdempotencyDecision.Type.REPLAY) {
            return new CreateResult(ownership.requireOwned(decision.resourceId().orElseThrow(), userId), true);
        }

        try {
            InterviewConfiguration configuration = new InterviewConfiguration(
                    command.targetPosition(), command.skills(), command.difficulty(), command.questionCount());
            InterviewSession session = InterviewSession.create(
                    UUID.randomUUID(), userId, configuration, clock.instant());
            InterviewSession saved = sessions.save(session);
            idempotency.complete(decision.recordId(), saved.id());
            return new CreateResult(saved, false);
        } catch (RuntimeException exception) {
            idempotency.abandon(decision.recordId());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public InterviewSessionPage list(UUID userId, Optional<InterviewStatus> status, int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 50");
        }
        return sessions.findPageByUserId(userId, status, page - 1, size);
    }

    @Transactional(readOnly = true)
    public InterviewSession get(UUID userId, UUID interviewId) {
        return ownership.requireOwned(interviewId, userId);
    }

    @Transactional(readOnly = true)
    public InterviewSession currentQuestion(UUID userId, UUID interviewId) {
        InterviewSession session = ownership.requireOwned(interviewId, userId);
        if (session.status() == InterviewStatus.CREATED) {
            throw new ApiException(ApiErrorCode.INTERVIEW_NOT_READY, "Interview has not started");
        }
        if (session.status() != InterviewStatus.IN_PROGRESS && session.status() != InterviewStatus.COMPLETED) {
            throw new ApiException(ApiErrorCode.ILLEGAL_INTERVIEW_STATE, "Interview has ended");
        }
        return session;
    }

    @Transactional
    public InterviewReport getReport(UUID userId, UUID interviewId) {
        InterviewSession session = requireCompleted(userId, interviewId);
        return ensurePendingReport(session);
    }

    @Transactional(noRollbackFor = AiClientException.class)
    public ReportResult retryReport(UUID userId, UUID interviewId, String idempotencyKey, String requestId) {
        IdempotencyDecision decision = idempotency.begin(
                userId, IdempotencyOperation.RETRY_INTERVIEW_REPORT, idempotencyKey, new ReportCommand(interviewId));
        InterviewSession session;
        try {
            session = requireCompleted(userId, interviewId);
        } catch (RuntimeException exception) {
            if (decision.type() == IdempotencyDecision.Type.PROCEED) {
                idempotency.abandon(decision.recordId());
            }
            throw exception;
        }
        if (decision.type() == IdempotencyDecision.Type.REPLAY) {
            return new ReportResult(ensurePendingReport(session), true);
        }

        try {
            InterviewReport report = ensurePendingReport(session);
            if (report.status() != com.interviewcopilot.business.interview.domain.ReportStatus.AVAILABLE) {
                report.beginGeneration();
                report = reports.save(report);
                ReportGenerationModels.Response generated;
                try {
                    generated = aiReports.generateReport(
                            toReportRequest(session),
                            new AiRequestContext(requestId, UUID.randomUUID().toString(), idempotencyKey));
                } catch (AiClientException exception) {
                    report.markGenerationFailed();
                    reports.save(report);
                    throw exception;
                }
                ReportGenerationModels.Metadata metadata = generated.metadata();
                report.publish(
                        new ReportSummary(generated.strengthSummary(), generated.weaknessSummary(),
                                generated.improvementSuggestions(), generated.overallComment()),
                        new AiCallMetadata(metadata.llmProvider(), metadata.modelName(), metadata.promptVersion(),
                                java.util.Map.of("input_tokens", metadata.tokenUsage().inputTokens().longValue(),
                                        "output_tokens", metadata.tokenUsage().outputTokens().longValue(),
                                        "total_tokens", metadata.tokenUsage().totalTokens().longValue()), metadata.latencyMs()),
                        "report-v1", clock.instant());
                report = reports.save(report);
            }
            idempotency.complete(decision.recordId(), report.id());
            return new ReportResult(report, false);
        } catch (RuntimeException exception) {
            idempotency.abandon(decision.recordId());
            throw exception;
        }
    }

    private InterviewSession requireCompleted(UUID userId, UUID interviewId) {
        InterviewSession session = ownership.requireOwned(interviewId, userId);
        if (session.status() != InterviewStatus.COMPLETED) {
            throw new ApiException(ApiErrorCode.INTERVIEW_NOT_COMPLETED, "Interview is not completed");
        }
        return session;
    }

    private InterviewReport ensurePendingReport(InterviewSession session) {
        return reports.findBySessionId(session.id())
                .orElseGet(() -> reports.save(InterviewReport.pending(UUID.randomUUID(), session, clock.instant())));
    }

    private ReportGenerationModels.Request toReportRequest(InterviewSession session) {
        InterviewMetrics metrics = session.metrics().orElseThrow();
        List<ReportGenerationModels.EvaluationSummary> evaluations = session.questions().stream()
                .map(question -> {
                    var evaluation = question.answer().orElseThrow().evaluation().orElseThrow();
                    return new ReportGenerationModels.EvaluationSummary(question.number(), evaluation.answerOverallScore(),
                            evaluation.strengths(), evaluation.missingPoints(), evaluation.feedback());
                }).toList();
        return new ReportGenerationModels.Request(session.configuration().targetPosition(), session.configuration().skills(),
                ReportGenerationModels.Difficulty.valueOf(session.configuration().difficulty().name()),
                new ReportGenerationModels.Metrics(metrics.interviewOverallScore(), metrics.questionCount(),
                        metrics.completedQuestionCount(), metrics.averageAccuracy(), metrics.averageCompleteness(),
                        metrics.averageDepth(), metrics.averageClarity(), metrics.durationSeconds()), evaluations, "zh-CN");
    }

    @Transactional
    public CancelResult cancel(UUID userId, UUID interviewId, String idempotencyKey) {
        IdempotencyDecision decision = idempotency.begin(
                userId, IdempotencyOperation.CANCEL_INTERVIEW, idempotencyKey, new CancelCommand(interviewId));
        if (decision.type() == IdempotencyDecision.Type.REPLAY) {
            return new CancelResult(ownership.requireOwned(decision.resourceId().orElse(interviewId), userId), true);
        }

        try {
            InterviewSession session = ownership.requireOwned(interviewId, userId);
            session.cancel(clock.instant());
            InterviewSession saved = sessions.save(session);
            idempotency.complete(decision.recordId(), saved.id());
            return new CancelResult(saved, false);
        } catch (RuntimeException exception) {
            idempotency.abandon(decision.recordId());
            throw exception;
        }
    }

    @Transactional
    public StartResult start(
            UUID userId,
            UUID interviewId,
            String idempotencyKey,
            String requestId
    ) {
        IdempotencyDecision decision = idempotency.begin(
                userId,
                IdempotencyOperation.START_INTERVIEW,
                idempotencyKey,
                new StartCommand(interviewId));
        if (decision.type() == IdempotencyDecision.Type.REPLAY) {
            InterviewSession session = ownership.requireOwned(decision.resourceId().orElse(interviewId), userId);
            return new StartResult(session, session.questions().getFirst(), true);
        }

        try {
            InterviewSession session = ownership.requireOwned(interviewId, userId);
            session.start(clock.instant());
            QuestionGenerationModels.Response generated = aiQuestions.generateQuestion(
                    new QuestionGenerationModels.Request(
                            session.configuration().targetPosition(),
                            session.configuration().skills(),
                            toAiDifficulty(session.configuration().difficulty()),
                            1,
                            session.configuration().questionCount(),
                            List.of(),
                            "zh-CN"),
                    new AiRequestContext(requestId, UUID.randomUUID().toString(), idempotencyKey));
            InterviewQuestion question = toQuestion(generated, session, clock.instant(), 1);
            session.addQuestion(question);
            InterviewSession saved = sessions.save(session);
            idempotency.complete(decision.recordId(), saved.id());
            return new StartResult(saved, saved.currentQuestion().orElseThrow(), false);
        } catch (RuntimeException exception) {
            idempotency.abandon(decision.recordId());
            throw exception;
        }
    }

    private InterviewQuestion toQuestion(
            QuestionGenerationModels.Response generated,
            InterviewSession session,
            Instant createdAt,
            int number
    ) {
        QuestionGenerationModels.Metadata metadata = generated.metadata();
        HashMap<String, Long> usage = new HashMap<>();
        usage.put("input_tokens", metadata.tokenUsage().inputTokens().longValue());
        usage.put("output_tokens", metadata.tokenUsage().outputTokens().longValue());
        usage.put("total_tokens", metadata.tokenUsage().totalTokens().longValue());
        return InterviewQuestion.create(
                UUID.randomUUID(),
                number,
                generated.question(),
                generated.topic(),
                Difficulty.valueOf(generated.difficulty().name()),
                generated.expectedPoints(),
                QuestionType.valueOf(generated.questionType().name()),
                new AiCallMetadata(
                        metadata.llmProvider(), metadata.modelName(), metadata.promptVersion(), usage,
                        metadata.latencyMs()),
                createdAt);
    }

    private QuestionGenerationModels.Difficulty toAiDifficulty(Difficulty difficulty) {
        return QuestionGenerationModels.Difficulty.valueOf(difficulty.name());
    }

    public record CreateCommand(
            String targetPosition,
            List<String> skills,
            Difficulty difficulty,
            Integer questionCount
    ) {
        public CreateCommand {
            questionCount = questionCount == null ? DEFAULT_QUESTION_COUNT : questionCount;
        }
    }

    public record CreateResult(InterviewSession session, boolean replay) {
    }

    public record StartCommand(UUID interviewId) {
    }

    public record StartResult(InterviewSession session, InterviewQuestion question, boolean replay) {
    }

    public record CancelCommand(UUID interviewId) {
    }

    public record CancelResult(InterviewSession session, boolean replay) {
    }

    public record ReportCommand(UUID interviewId) {
    }

    public record ReportResult(InterviewReport report, boolean replay) {
    }

    public record SubmitCommand(UUID questionId, String answerContent) {}
    public record SubmitIdentity(UUID interviewId, UUID questionId, String answerContent) {}
    public record SubmitResult(InterviewSession session, InterviewQuestion answeredQuestion, boolean replay) {}
}
