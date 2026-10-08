package com.interviewcopilot.business.interview.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class InterviewAnswer {
    private final UUID id;
    private final UUID questionId;
    private final String content;
    private final Instant submittedAt;
    private AnswerEvaluation evaluation;

    private InterviewAnswer(UUID id, UUID questionId, String content, Instant submittedAt) {
        this.id = DomainChecks.required(id, "id");
        this.questionId = DomainChecks.required(questionId, "questionId");
        this.content = DomainChecks.requiredText(content, "content", 8000);
        this.submittedAt = DomainChecks.required(submittedAt, "submittedAt");
    }

    public static InterviewAnswer create(UUID id, UUID questionId, String content, Instant submittedAt) {
        return new InterviewAnswer(id, questionId, content, submittedAt);
    }

    public static InterviewAnswer reconstitute(
            UUID id,
            UUID questionId,
            String content,
            Instant submittedAt,
            AnswerEvaluation evaluation
    ) {
        InterviewAnswer answer = create(id, questionId, content, submittedAt);
        if (evaluation != null) {
            answer.recordEvaluation(evaluation);
        }
        return answer;
    }

    void recordEvaluation(AnswerEvaluation evaluation) {
        DomainChecks.required(evaluation, "evaluation");
        if (this.evaluation != null) {
            throw new DomainRuleViolationException("An answer can have only one evaluation");
        }
        if (!id.equals(evaluation.answerId())) {
            throw new DomainRuleViolationException("Evaluation does not belong to this answer");
        }
        DomainChecks.notBefore(evaluation.createdAt(), submittedAt, "evaluation.createdAt");
        this.evaluation = evaluation;
    }

    public UUID id() {
        return id;
    }

    public UUID questionId() {
        return questionId;
    }

    public String content() {
        return content;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Optional<AnswerEvaluation> evaluation() {
        return Optional.ofNullable(evaluation);
    }

    public boolean isEvaluated() {
        return evaluation != null;
    }
}
