package com.interviewcopilot.business.interview.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class InterviewSession {
    private final UUID id;
    private final UUID userId;
    private final InterviewConfiguration configuration;
    private final Instant createdAt;
    private final List<InterviewQuestion> questions = new ArrayList<>();
    private InterviewStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private InterviewMetrics metrics;
    private Long persistenceVersion;

    private InterviewSession(
            UUID id,
            UUID userId,
            InterviewConfiguration configuration,
            Instant createdAt
    ) {
        this.id = DomainChecks.required(id, "id");
        this.userId = DomainChecks.required(userId, "userId");
        this.configuration = DomainChecks.required(configuration, "configuration");
        this.createdAt = DomainChecks.required(createdAt, "createdAt");
        this.status = InterviewStatus.CREATED;
    }

    public static InterviewSession create(
            UUID id,
            UUID userId,
            InterviewConfiguration configuration,
            Instant createdAt
    ) {
        return new InterviewSession(id, userId, configuration, createdAt);
    }

    public static InterviewSession reconstitute(
            UUID id,
            UUID userId,
            InterviewConfiguration configuration,
            InterviewStatus status,
            List<InterviewQuestion> questions,
            Integer currentQuestionNumber,
            BigDecimal persistedOverallScore,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            long persistenceVersion
    ) {
        DomainChecks.required(status, "status");
        DomainChecks.required(questions, "questions");
        if (persistenceVersion < 0) {
            throw new DomainValidationException("persistenceVersion must not be negative");
        }

        InterviewSession session = create(id, userId, configuration, createdAt);
        if (startedAt != null) {
            session.start(startedAt);
            for (InterviewQuestion question : questions) {
                session.addQuestion(question);
            }
        } else if (!questions.isEmpty()) {
            throw new DomainValidationException("An interview with questions must have a start time");
        }
        session.validateCurrentQuestionNumber(currentQuestionNumber);

        switch (status) {
            case CREATED -> {
                requireNull(startedAt, "startedAt", status);
                requireNull(completedAt, "completedAt", status);
                requireNull(cancelledAt, "cancelledAt", status);
                requireNull(persistedOverallScore, "persistedOverallScore", status);
            }
            case IN_PROGRESS -> {
                requirePresent(startedAt, "startedAt", status);
                requireNull(completedAt, "completedAt", status);
                requireNull(cancelledAt, "cancelledAt", status);
                requireNull(persistedOverallScore, "persistedOverallScore", status);
            }
            case COMPLETED -> {
                requirePresent(startedAt, "startedAt", status);
                requirePresent(completedAt, "completedAt", status);
                requireNull(cancelledAt, "cancelledAt", status);
                InterviewMetrics restoredMetrics = session.complete(completedAt);
                BigDecimal storedScore = ScoreCalculator.dimensionScore(
                        persistedOverallScore, "persistedOverallScore");
                if (restoredMetrics.interviewOverallScore().compareTo(storedScore) != 0) {
                    throw new DomainValidationException(
                            "persistedOverallScore does not match the evaluated answers");
                }
            }
            case CANCELLED -> {
                requirePresent(cancelledAt, "cancelledAt", status);
                requireNull(completedAt, "completedAt", status);
                requireNull(persistedOverallScore, "persistedOverallScore", status);
                session.cancel(cancelledAt);
            }
            case FAILED -> {
                requirePresent(startedAt, "startedAt", status);
                requireNull(completedAt, "completedAt", status);
                requireNull(cancelledAt, "cancelledAt", status);
                requireNull(persistedOverallScore, "persistedOverallScore", status);
                session.markUnrecoverableFailure();
            }
        }
        session.persistenceVersion = persistenceVersion;
        return session;
    }

    private void validateCurrentQuestionNumber(Integer persistedCurrentQuestionNumber) {
        Integer derived = currentQuestionNumber().orElse(null);
        if (!java.util.Objects.equals(derived, persistedCurrentQuestionNumber)) {
            throw new DomainValidationException(
                    "currentQuestionNumber does not match the persisted question sequence");
        }
    }

    private static void requireNull(Object value, String field, InterviewStatus status) {
        if (value != null) {
            throw new DomainValidationException(field + " must be null for status " + status);
        }
    }

    private static void requirePresent(Object value, String field, InterviewStatus status) {
        if (value == null) {
            throw new DomainValidationException(field + " is required for status " + status);
        }
    }

    public void start(Instant startedAt) {
        requireStatus(InterviewStatus.CREATED, "start");
        this.startedAt = DomainChecks.notBefore(startedAt, createdAt, "startedAt");
        status = InterviewStatus.IN_PROGRESS;
    }

    public void addQuestion(InterviewQuestion question) {
        requireStatus(InterviewStatus.IN_PROGRESS, "add a question");
        DomainChecks.required(question, "question");
        int expectedNumber = questions.size() + 1;
        if (expectedNumber > configuration.questionCount()) {
            throw new DomainRuleViolationException("The configured question count has already been reached");
        }
        if (question.number() != expectedNumber) {
            throw new DomainRuleViolationException("Question numbers must start at 1 and increase continuously");
        }
        if (question.difficulty() != configuration.difficulty()) {
            throw new DomainRuleViolationException("Question difficulty must match the interview difficulty");
        }
        if (questions.stream().anyMatch(existing -> existing.id().equals(question.id()))) {
            throw new DomainRuleViolationException("Question id must be unique within an interview");
        }
        if (!questions.isEmpty() && !questions.getLast().isEvaluated()) {
            throw new DomainRuleViolationException(
                    "The current question must be answered and evaluated before adding the next question");
        }
        DomainChecks.notBefore(question.createdAt(), startedAt, "question.createdAt");
        questions.add(question);
    }

    public void submitAnswer(UUID questionId, InterviewAnswer answer) {
        requireStatus(InterviewStatus.IN_PROGRESS, "submit an answer");
        InterviewQuestion current = requireCurrentQuestion();
        if (!current.id().equals(DomainChecks.required(questionId, "questionId"))) {
            throw new DomainRuleViolationException("Only the current question can be answered");
        }
        current.submitAnswer(answer);
    }

    public void recordEvaluation(UUID questionId, AnswerEvaluation evaluation) {
        requireStatus(InterviewStatus.IN_PROGRESS, "record an evaluation");
        InterviewQuestion current = requireCurrentQuestion();
        if (!current.id().equals(DomainChecks.required(questionId, "questionId"))) {
            throw new DomainRuleViolationException("Only the current question can be evaluated");
        }
        current.recordEvaluation(evaluation);
    }

    public InterviewMetrics complete(Instant completedAt) {
        requireStatus(InterviewStatus.IN_PROGRESS, "complete");
        Instant normalizedCompletedAt = DomainChecks.notBefore(completedAt, startedAt, "completedAt");
        if (questions.size() != configuration.questionCount()) {
            throw new DomainRuleViolationException("All configured questions must exist before completion");
        }
        if (questions.stream().anyMatch(question -> !question.isEvaluated())) {
            throw new DomainRuleViolationException("Every question must have an answer and evaluation before completion");
        }

        List<AnswerEvaluation> evaluations = questions.stream()
                .map(question -> question.answer().orElseThrow().evaluation().orElseThrow())
                .toList();
        metrics = new InterviewMetrics(
                ScoreCalculator.average(evaluations.stream().map(AnswerEvaluation::answerOverallScore).toList()),
                configuration.questionCount(),
                evaluations.size(),
                ScoreCalculator.average(evaluations.stream().map(AnswerEvaluation::accuracy).toList()),
                ScoreCalculator.average(evaluations.stream().map(AnswerEvaluation::completeness).toList()),
                ScoreCalculator.average(evaluations.stream().map(AnswerEvaluation::depth).toList()),
                ScoreCalculator.average(evaluations.stream().map(AnswerEvaluation::clarity).toList()),
                Duration.between(startedAt, normalizedCompletedAt).getSeconds());
        this.completedAt = normalizedCompletedAt;
        status = InterviewStatus.COMPLETED;
        return metrics;
    }

    public void cancel(Instant cancelledAt) {
        if (status != InterviewStatus.CREATED && status != InterviewStatus.IN_PROGRESS) {
            throw new DomainRuleViolationException("Interview in status " + status + " cannot be cancelled");
        }
        Instant lowerBound = startedAt == null ? createdAt : startedAt;
        this.cancelledAt = DomainChecks.notBefore(cancelledAt, lowerBound, "cancelledAt");
        status = InterviewStatus.CANCELLED;
    }

    public void markUnrecoverableFailure() {
        requireStatus(InterviewStatus.IN_PROGRESS, "mark as failed");
        status = InterviewStatus.FAILED;
    }

    private InterviewQuestion requireCurrentQuestion() {
        if (questions.isEmpty()) {
            throw new DomainRuleViolationException("The interview has no current question");
        }
        return questions.getLast();
    }

    private void requireStatus(InterviewStatus expected, String operation) {
        if (status != expected) {
            throw new DomainRuleViolationException(
                    "Interview in status " + status + " cannot " + operation);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public InterviewConfiguration configuration() {
        return configuration;
    }

    public InterviewStatus status() {
        return status;
    }

    public List<InterviewQuestion> questions() {
        return List.copyOf(questions);
    }

    public Optional<InterviewQuestion> currentQuestion() {
        return questions.isEmpty() ? Optional.empty() : Optional.of(questions.getLast());
    }

    public Optional<Integer> currentQuestionNumber() {
        return currentQuestion().map(InterviewQuestion::number);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    public Optional<BigDecimal> interviewOverallScore() {
        return Optional.ofNullable(metrics).map(InterviewMetrics::interviewOverallScore);
    }

    public Optional<InterviewMetrics> metrics() {
        return Optional.ofNullable(metrics);
    }

    public OptionalLong persistenceVersion() {
        return persistenceVersion == null ? OptionalLong.empty() : OptionalLong.of(persistenceVersion);
    }
}
