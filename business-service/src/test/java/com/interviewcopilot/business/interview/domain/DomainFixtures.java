package com.interviewcopilot.business.interview.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class DomainFixtures {
    private DomainFixtures() {
    }

    static InterviewQuestion question(int number, Instant createdAt) {
        return InterviewQuestion.create(
                UUID.randomUUID(),
                number,
                "Question " + number,
                "Topic " + number,
                Difficulty.MEDIUM,
                List.of("Expected point " + number),
                QuestionType.CONCEPTUAL,
                metadata("question-v1"),
                createdAt);
    }

    static InterviewAnswer answer(InterviewQuestion question, int sequence) {
        return InterviewAnswer.create(
                UUID.randomUUID(), question.id(), "Answer " + sequence, question.createdAt().plusSeconds(1));
    }

    static AnswerEvaluation evaluation(InterviewAnswer answer, int sequence) {
        BigDecimal score = new BigDecimal(70 + sequence + ".00");
        return AnswerEvaluation.create(
                UUID.randomUUID(), answer.id(), score, score, score, score,
                List.of("Strength"), List.of("Missing point"), "Feedback",
                metadata("evaluation-v1"), "rubric-v1",
                answer.submittedAt().plusSeconds(1));
    }

    static AiCallMetadata metadata(String promptVersion) {
        return new AiCallMetadata(
                "mock", "mock-model", promptVersion,
                Map.of("input_tokens", 10L, "output_tokens", 5L, "total_tokens", 15L), 20L);
    }
}
