package com.interviewcopilot.business.interview.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InterviewQuestion {
    private final UUID id;
    private final int number;
    private final String text;
    private final String topic;
    private final Difficulty difficulty;
    private final List<String> expectedPoints;
    private final QuestionType type;
    private final AiCallMetadata generationMetadata;
    private final Instant createdAt;
    private InterviewAnswer answer;

    private InterviewQuestion(
            UUID id,
            int number,
            String text,
            String topic,
            Difficulty difficulty,
            List<String> expectedPoints,
            QuestionType type,
            AiCallMetadata generationMetadata,
            Instant createdAt
    ) {
        this.id = DomainChecks.required(id, "id");
        if (number < 1 || number > InterviewConfiguration.MAX_QUESTION_COUNT) {
            throw new DomainValidationException("number must be between 1 and 10");
        }
        this.number = number;
        this.text = DomainChecks.requiredText(text, "text", 4000);
        this.topic = DomainChecks.requiredText(topic, "topic", 200);
        this.difficulty = DomainChecks.required(difficulty, "difficulty");
        this.expectedPoints = DomainChecks.uniqueTextList(
                expectedPoints, "expectedPoints", 1, 20, 1000);
        this.type = DomainChecks.required(type, "type");
        this.generationMetadata = DomainChecks.required(generationMetadata, "generationMetadata");
        this.createdAt = DomainChecks.required(createdAt, "createdAt");
    }

    public static InterviewQuestion create(
            UUID id,
            int number,
            String text,
            String topic,
            Difficulty difficulty,
            List<String> expectedPoints,
            QuestionType type,
            AiCallMetadata generationMetadata,
            Instant createdAt
    ) {
        return new InterviewQuestion(
                id, number, text, topic, difficulty, expectedPoints, type, generationMetadata, createdAt);
    }

    public static InterviewQuestion reconstitute(
            UUID id,
            int number,
            String text,
            String topic,
            Difficulty difficulty,
            List<String> expectedPoints,
            QuestionType type,
            AiCallMetadata generationMetadata,
            Instant createdAt,
            InterviewAnswer answer
    ) {
        InterviewQuestion question = create(
                id, number, text, topic, difficulty, expectedPoints, type, generationMetadata, createdAt);
        if (answer != null) {
            question.submitAnswer(answer);
        }
        return question;
    }

    void submitAnswer(InterviewAnswer answer) {
        DomainChecks.required(answer, "answer");
        if (this.answer != null) {
            throw new DomainRuleViolationException("A question can have only one formal answer");
        }
        if (!id.equals(answer.questionId())) {
            throw new DomainRuleViolationException("Answer does not belong to this question");
        }
        DomainChecks.notBefore(answer.submittedAt(), createdAt, "answer.submittedAt");
        this.answer = answer;
    }

    void recordEvaluation(AnswerEvaluation evaluation) {
        if (answer == null) {
            throw new DomainRuleViolationException("A question must be answered before it can be evaluated");
        }
        answer.recordEvaluation(evaluation);
    }

    public UUID id() {
        return id;
    }

    public int number() {
        return number;
    }

    public String text() {
        return text;
    }

    public String topic() {
        return topic;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public List<String> expectedPoints() {
        return expectedPoints;
    }

    public QuestionType type() {
        return type;
    }

    public AiCallMetadata generationMetadata() {
        return generationMetadata;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<InterviewAnswer> answer() {
        return Optional.ofNullable(answer);
    }

    public boolean isEvaluated() {
        return answer != null && answer.isEvaluated();
    }
}
