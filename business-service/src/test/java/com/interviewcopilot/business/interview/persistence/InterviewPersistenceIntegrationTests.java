package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.InterviewCopilotBusinessApplication;
import com.interviewcopilot.business.integration.ai.AiClientException;
import com.interviewcopilot.business.interview.application.InterviewApplicationService;
import com.interviewcopilot.business.interview.domain.AiCallMetadata;
import com.interviewcopilot.business.interview.domain.AnswerEvaluation;
import com.interviewcopilot.business.interview.domain.Difficulty;
import com.interviewcopilot.business.interview.domain.InterviewAnswer;
import com.interviewcopilot.business.interview.domain.InterviewConfiguration;
import com.interviewcopilot.business.interview.domain.InterviewQuestion;
import com.interviewcopilot.business.interview.domain.InterviewReport;
import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.domain.InterviewStatus;
import com.interviewcopilot.business.interview.domain.QuestionType;
import com.interviewcopilot.business.interview.domain.ReportStatus;
import com.interviewcopilot.business.interview.domain.ReportSummary;
import com.interviewcopilot.business.interview.repository.InterviewReportRepository;
import com.interviewcopilot.business.interview.repository.InterviewSessionPage;
import com.interviewcopilot.business.interview.repository.InterviewSessionRepository;
import com.interviewcopilot.business.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_RUN", matches = "true")
class InterviewPersistenceIntegrationTests {
    private static final Instant BASE_TIME = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    void repositoryPersistsAggregatesEnforcesConstraintsAndConcurrencyAndPaginates() throws Exception {
        String localBaseUrl = System.getenv("MIGRATION_TEST_JDBC_BASE_URL");
        if (localBaseUrl != null) {
            verifyAgainstIsolatedLocalSchema(localBaseUrl);
            return;
        }

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11")
                .withDatabaseName("interviewcopilot_p1_t05_test")
                .withUsername("persistence_test")
                .withPassword("persistence_test")) {
            mysql.start();
            verifyPersistence(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }

    private void verifyAgainstIsolatedLocalSchema(String baseUrl) throws Exception {
        assertTrue(baseUrl.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/$"),
                "Local persistence tests require loopback MySQL and no selected schema");
        String user = System.getenv().getOrDefault("MIGRATION_TEST_DB_USER", "root");
        String password = System.getenv().getOrDefault("MIGRATION_TEST_DB_PASSWORD", "");
        String schema = "interviewcopilot_p1_t05_it_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection admin = DriverManager.getConnection(baseUrl, user, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
            try {
                verifyPersistence(baseUrl + schema, user, password);
            } finally {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    private void verifyPersistence(String jdbcUrl, String user, String password) {
        try (ConfigurableApplicationContext context = startBusinessService(jdbcUrl, user, password)) {
            InterviewSessionRepository sessions = context.getBean(InterviewSessionRepository.class);
            InterviewReportRepository reports = context.getBean(InterviewReportRepository.class);
            UserRepository users = context.getBean(UserRepository.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            UUID ownerId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            users.insert(ownerId, "PersistenceOwner", "test-hash", BASE_TIME.minusSeconds(60));
            users.insert(otherUserId, "PersistenceOther", "test-hash", BASE_TIME.minusSeconds(60));

            InterviewSession completed = sessions.save(completedSession(ownerId, BASE_TIME));
            assertEquals(0L, completed.persistenceVersion().orElseThrow());
            assertAggregateRoundTrip(context, sessions, completed);
            assertQuestionNumberUniqueConstraint(jdbc, completed.id());
            assertReportRoundTrip(reports, completed);
            assertPaginationUsesBoundedQueries(context, sessions, ownerId, otherUserId, completed.id());
            assertOptimisticLocking(sessions, ownerId);

            InterviewSession failedReportSession = sessions.save(completedSession(ownerId, BASE_TIME.plusSeconds(600)));
            InterviewApplicationService application = context.getBean(InterviewApplicationService.class);
            application.getReport(ownerId, failedReportSession.id());
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThrows(AiClientException.class, () -> application.retryReport(ownerId, failedReportSession.id(),
                        "failed-report-retry-key", "report-failure-test"));
                assertEquals(ReportStatus.FAILED_RETRYABLE, reports.findBySessionId(failedReportSession.id()).orElseThrow().status());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_records WHERE operation = 'RETRY_INTERVIEW_REPORT'", Integer.class));
            }

        }
    }

    private ConfigurableApplicationContext startBusinessService(
            String jdbcUrl,
            String user,
            String password
    ) {
        return new SpringApplicationBuilder(InterviewCopilotBusinessApplication.class)
                .web(WebApplicationType.NONE)
                .initializers(application -> application.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("interview-persistence-test", Map.of(
                                "spring.datasource.url", jdbcUrl,
                                "spring.datasource.username", user,
                                "spring.datasource.password", password,
                                "interviewcopilot.ai-service.service-token", "persistence-test-token",
                                "interviewcopilot.ai-service.base-url", "http://127.0.0.1:1",
                                "interviewcopilot.security.jwt.signing-secret",
                                "persistence-test-jwt-signing-key-32-bytes"
                        ))))
                .run();
    }

    private void assertAggregateRoundTrip(
            ConfigurableApplicationContext context,
            InterviewSessionRepository sessions,
            InterviewSession completed
    ) {
        Statistics statistics = statistics(context);
        statistics.clear();
        InterviewSession restored = sessions.findById(completed.id()).orElseThrow();
        assertEquals(InterviewStatus.COMPLETED, restored.status());
        assertEquals(3, restored.questions().size());
        assertEquals(completed.metrics(), restored.metrics());
        assertEquals("mock-model", restored.questions().getFirst().generationMetadata().modelName());
        assertEquals("evaluation-v1", restored.questions().getFirst().answer().orElseThrow()
                .evaluation().orElseThrow().evaluationVersion());
        assertEquals(1, statistics.getPrepareStatementCount(),
                "A complete aggregate must load with one query");
        assertTrue(sessions.findByIdAndUserId(completed.id(), completed.userId()).isPresent());
        assertFalse(sessions.findByIdAndUserId(completed.id(), UUID.randomUUID()).isPresent());
    }

    private void assertQuestionNumberUniqueConstraint(JdbcTemplate jdbc, UUID sessionId) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO interview_questions
                    (id, session_id, question_number, question_text, topic, difficulty,
                     expected_points, question_type, llm_provider, model_name, prompt_version, created_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), UNHEX(REPLACE(?, '-', '')), 1, 'Duplicate number', 'Persistence',
                        'MEDIUM', JSON_ARRAY('Point'), 'CONCEPTUAL', 'mock', 'mock-model',
                        'question-v1', UTC_TIMESTAMP(6))
                """, UUID.randomUUID().toString(), sessionId.toString()));
    }

    private void assertReportRoundTrip(
            InterviewReportRepository reports,
            InterviewSession completed
    ) {
        InterviewReport report = InterviewReport.pending(
                UUID.randomUUID(), completed, completed.completedAt().orElseThrow().plusSeconds(1));
        report.beginGeneration();
        report.publish(
                new ReportSummary(
                        "Strong fundamentals",
                        "Needs more depth",
                        List.of("Practice system design"),
                        "Good result"),
                metadata("report-prompt-v1"),
                "report-v1",
                report.createdAt().plusSeconds(1));

        InterviewReport saved = reports.save(report);
        InterviewReport restored = reports.findBySessionId(completed.id()).orElseThrow();
        assertEquals(0L, saved.persistenceVersion().orElseThrow());
        assertEquals(ReportStatus.AVAILABLE, restored.status());
        assertEquals(completed.metrics().orElseThrow(), restored.metrics());
        assertEquals("report-v1", restored.reportVersion().orElseThrow());
        assertEquals("Strong fundamentals", restored.summary().orElseThrow().strengthSummary());
        assertTrue(reports.findBySessionId(UUID.randomUUID()).isEmpty());

        InterviewReport firstWriter = reports.findBySessionId(completed.id()).orElseThrow();
        InterviewReport staleWriter = reports.findBySessionId(completed.id()).orElseThrow();
        InterviewReport updated = reports.save(firstWriter);
        assertEquals(1L, updated.persistenceVersion().orElseThrow());
        assertThrows(OptimisticLockingFailureException.class, () -> reports.save(staleWriter));
    }

    private void assertPaginationUsesBoundedQueries(
            ConfigurableApplicationContext context,
            InterviewSessionRepository sessions,
            UUID ownerId,
            UUID otherUserId,
            UUID completedSessionId
    ) {
        InterviewSession created = sessions.save(newSession(ownerId, BASE_TIME.plusSeconds(100)));
        InterviewSession cancelled = newSession(ownerId, BASE_TIME.plusSeconds(200));
        cancelled.cancel(BASE_TIME.plusSeconds(201));
        cancelled = sessions.save(cancelled);
        InterviewSession newest = sessions.save(newSession(ownerId, BASE_TIME.plusSeconds(300)));
        sessions.save(newSession(otherUserId, BASE_TIME.plusSeconds(400)));

        Statistics statistics = statistics(context);
        statistics.clear();
        InterviewSessionPage firstPage = sessions.findPageByUserId(
                ownerId, Optional.empty(), 0, 2);

        assertEquals(List.of(newest.id(), cancelled.id()),
                firstPage.content().stream().map(InterviewSession::id).toList());
        assertEquals(4, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertTrue(statistics.getPrepareStatementCount() <= 3,
                "Pagination must use an id page plus one aggregate query, not N+1 loading");

        statistics.clear();
        InterviewSessionPage secondPage = sessions.findPageByUserId(
                ownerId, Optional.empty(), 1, 2);
        assertTrue(secondPage.content().stream()
                .anyMatch(session -> session.id().equals(completedSessionId)));
        assertTrue(statistics.getPrepareStatementCount() <= 3,
                "Pagination query count must stay bounded for aggregates with nested children");

        InterviewSessionPage cancelledPage = sessions.findPageByUserId(
                ownerId, Optional.of(InterviewStatus.CANCELLED), 0, 10);
        assertEquals(List.of(cancelled.id()),
                cancelledPage.content().stream().map(InterviewSession::id).toList());
        assertTrue(cancelledPage.content().stream()
                .noneMatch(session -> session.id().equals(created.id())
                        || session.id().equals(completedSessionId)));
    }

    private void assertOptimisticLocking(InterviewSessionRepository sessions, UUID ownerId) {
        InterviewSession saved = sessions.save(newSession(ownerId, BASE_TIME.plusSeconds(500)));
        InterviewSession firstWriter = sessions.findById(saved.id()).orElseThrow();
        InterviewSession staleWriter = sessions.findById(saved.id()).orElseThrow();

        firstWriter.start(BASE_TIME.plusSeconds(501));
        InterviewSession updated = sessions.save(firstWriter);
        assertEquals(1L, updated.persistenceVersion().orElseThrow());

        staleWriter.cancel(BASE_TIME.plusSeconds(502));
        assertThrows(OptimisticLockingFailureException.class, () -> sessions.save(staleWriter));
        assertEquals(InterviewStatus.IN_PROGRESS, sessions.findById(saved.id()).orElseThrow().status());
    }

    private Statistics statistics(ConfigurableApplicationContext context) {
        Statistics statistics = context.getBean(EntityManagerFactory.class)
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    private InterviewSession completedSession(UUID userId, Instant createdAt) {
        InterviewSession session = newSession(userId, createdAt);
        Instant startedAt = createdAt.plusSeconds(1);
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
                    UUID.randomUUID(),
                    question.id(),
                    "Answer " + number,
                    question.createdAt().plusSeconds(1));
            session.submitAnswer(question.id(), answer);
            BigDecimal score = new BigDecimal(70 + number + ".00");
            session.recordEvaluation(question.id(), AnswerEvaluation.create(
                    UUID.randomUUID(),
                    answer.id(),
                    score,
                    score,
                    score,
                    score,
                    List.of("Clear"),
                    List.of("More depth"),
                    "Good answer",
                    metadata("evaluation-prompt-v1"),
                    "evaluation-v1",
                    answer.submittedAt().plusSeconds(1)));
        }
        session.complete(createdAt.plusSeconds(30));
        return session;
    }

    private InterviewSession newSession(UUID userId, Instant createdAt) {
        return InterviewSession.create(
                UUID.randomUUID(),
                userId,
                new InterviewConfiguration(
                        "Java Engineer", List.of("Java", "Spring Boot"), Difficulty.MEDIUM, 3),
                createdAt);
    }

    private AiCallMetadata metadata(String promptVersion) {
        return new AiCallMetadata(
                "mock",
                "mock-model",
                promptVersion,
                Map.of("input_tokens", 10L, "output_tokens", 5L, "total_tokens", 15L),
                20L);
    }
}
