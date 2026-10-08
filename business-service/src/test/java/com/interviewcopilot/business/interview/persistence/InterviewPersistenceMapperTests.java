package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.interview.domain.AiCallMetadata;
import com.interviewcopilot.business.interview.domain.AnswerEvaluation;
import com.interviewcopilot.business.interview.domain.Difficulty;
import com.interviewcopilot.business.interview.domain.DomainValidationException;
import com.interviewcopilot.business.interview.domain.InterviewAnswer;
import com.interviewcopilot.business.interview.domain.InterviewConfiguration;
import com.interviewcopilot.business.interview.domain.InterviewQuestion;
import com.interviewcopilot.business.interview.domain.InterviewReport;
import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.domain.InterviewStatus;
import com.interviewcopilot.business.interview.domain.QuestionType;
import com.interviewcopilot.business.interview.domain.ReportStatus;
import com.interviewcopilot.business.interview.domain.ReportSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewPersistenceMapperTests {
    private static final Instant CREATED_AT = Instant.parse("2026-10-08T04:00:00Z");

    @Test
    void roundTripsACompletedAggregateWithNestedChildrenAndAiMetadata() {
        InterviewSession source = completedSession();

        InterviewSessionEntity entity = InterviewPersistenceMapper.toEntity(
                source, CREATED_AT.plusSeconds(500));
        entity.version = 7L;
        InterviewSession restored = InterviewPersistenceMapper.toDomain(entity);

        assertEquals(source.id(), restored.id());
        assertEquals(source.userId(), restored.userId());
        assertEquals(InterviewStatus.COMPLETED, restored.status());
        assertEquals(source.configuration(), restored.configuration());
        assertEquals(source.interviewOverallScore(), restored.interviewOverallScore());
        assertEquals(7L, restored.persistenceVersion().orElseThrow());
        assertEquals(3, restored.questions().size());
        InterviewQuestion restoredQuestion = restored.questions().getFirst();
        assertEquals("mock-model", restoredQuestion.generationMetadata().modelName());
        AnswerEvaluation restoredEvaluation = restoredQuestion.answer().orElseThrow()
                .evaluation().orElseThrow();
        assertEquals("rubric-v1", restoredEvaluation.evaluationVersion());
        assertEquals(15L, restoredEvaluation.evaluationMetadata().tokenUsage().get("total_tokens"));
    }

    @Test
    void roundTripsAvailableReportWithoutAllowingAiToReplaceMetrics() {
        InterviewSession completedSession = completedSession();
        InterviewReport source = InterviewReport.pending(
                UUID.randomUUID(), completedSession, CREATED_AT.plusSeconds(400));
        source.beginGeneration();
        source.publish(
                new ReportSummary(
                        "Strong fundamentals", "Needs more depth", List.of("Practice design"), "Good result"),
                metadata("report-v1"),
                "report-v1",
                CREATED_AT.plusSeconds(420));

        InterviewSessionEntity sessionReference = InterviewPersistenceMapper.toEntity(
                completedSession, CREATED_AT.plusSeconds(500));
        sessionReference.version = 1L;
        InterviewReportEntity entity = InterviewPersistenceMapper.toEntity(
                source, sessionReference, CREATED_AT.plusSeconds(500));
        entity.version = 3L;
        InterviewReport restored = InterviewPersistenceMapper.toDomain(entity, completedSession);

        assertEquals(ReportStatus.AVAILABLE, restored.status());
        assertEquals(completedSession.metrics().orElseThrow(), restored.metrics());
        assertEquals("Strong fundamentals", restored.summary().orElseThrow().strengthSummary());
        assertEquals("report-v1", restored.reportVersion().orElseThrow());
        assertEquals(3L, restored.persistenceVersion().orElseThrow());
    }

    @Test
    void rejectsPersistenceSnapshotsThatContradictDomainInvariants() {
        InterviewSessionEntity entity = InterviewPersistenceMapper.toEntity(
                completedSession(), CREATED_AT.plusSeconds(500));
        entity.version = 1L;
        entity.currentQuestionNumber = 2;
        assertThrows(DomainValidationException.class,
                () -> InterviewPersistenceMapper.toDomain(entity));

        entity.currentQuestionNumber = 3;
        entity.interviewOverallScore = new BigDecimal("99.99");
        assertThrows(DomainValidationException.class,
                () -> InterviewPersistenceMapper.toDomain(entity));
    }

    @Test
    void preservesNewVersusPersistedRevisionSemantics() {
        InterviewSession source = InterviewSession.create(
                UUID.randomUUID(), UUID.randomUUID(), configuration(), CREATED_AT);
        assertFalse(source.persistenceVersion().isPresent());

        InterviewSessionEntity entity = InterviewPersistenceMapper.toEntity(source, CREATED_AT.plusSeconds(1));
        assertTrue(entity.isNew());
        entity.version = 0L;

        InterviewSession restored = InterviewPersistenceMapper.toDomain(entity);
        assertEquals(0L, restored.persistenceVersion().orElseThrow());
    }

    private InterviewSession completedSession() {
        InterviewSession session = InterviewSession.create(
                UUID.randomUUID(), UUID.randomUUID(), configuration(), CREATED_AT);
        Instant startedAt = CREATED_AT.plusSeconds(10);
        session.start(startedAt);
        for (int number = 1; number <= 3; number++) {
            InterviewQuestion question = InterviewQuestion.create(
                    UUID.randomUUID(),
                    number,
                    "Question " + number,
                    "Topic " + number,
                    Difficulty.MEDIUM,
                    List.of("Expected point " + number),
                    QuestionType.CONCEPTUAL,
                    metadata("question-v1"),
                    startedAt.plusSeconds(number));
            session.addQuestion(question);
            InterviewAnswer answer = InterviewAnswer.create(
                    UUID.randomUUID(), question.id(), "Answer " + number,
                    question.createdAt().plusSeconds(1));
            session.submitAnswer(question.id(), answer);
            BigDecimal score = new BigDecimal(70 + number + ".00");
            session.recordEvaluation(question.id(), AnswerEvaluation.create(
                    UUID.randomUUID(), answer.id(), score, score, score, score,
                    List.of("Strength"), List.of("Missing point"), "Feedback",
                    metadata("evaluation-v1"), "rubric-v1",
                    answer.submittedAt().plusSeconds(1)));
        }
        session.complete(CREATED_AT.plusSeconds(300));
        return session;
    }

    private InterviewConfiguration configuration() {
        return new InterviewConfiguration(
                "Java Engineer", List.of("Java", "Spring Boot"), Difficulty.MEDIUM, 3);
    }

    private AiCallMetadata metadata(String promptVersion) {
        return new AiCallMetadata(
                "mock", "mock-model", promptVersion,
                Map.of("input_tokens", 10L, "output_tokens", 5L, "total_tokens", 15L), 20L);
    }
}
