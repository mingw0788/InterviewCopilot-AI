package com.interviewcopilot.business.idempotency;

import com.interviewcopilot.business.InterviewCopilotBusinessApplication;
import com.interviewcopilot.business.user.UserRepository;
import com.interviewcopilot.business.web.ApiErrorCode;
import com.interviewcopilot.business.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_RUN", matches = "true")
class IdempotencyPersistenceIntegrationTests {
    private static final Instant BASE_TIME = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    void claimsAreUserAndOperationScopedCaseSensitiveAndReplayTheOriginalResource() throws Exception {
        String localBaseUrl = System.getenv("MIGRATION_TEST_JDBC_BASE_URL");
        if (localBaseUrl != null) {
            verifyAgainstIsolatedLocalSchema(localBaseUrl);
            return;
        }

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11")
                .withDatabaseName("interviewcopilot_p1_t06_test")
                .withUsername("idempotency_test")
                .withPassword("idempotency_test")) {
            mysql.start();
            verifyPersistence(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }

    private void verifyAgainstIsolatedLocalSchema(String baseUrl) throws Exception {
        assertTrue(baseUrl.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/$"),
                "Local persistence tests require loopback MySQL and no selected schema");
        String user = System.getenv().getOrDefault("MIGRATION_TEST_DB_USER", "root");
        String password = System.getenv().getOrDefault("MIGRATION_TEST_DB_PASSWORD", "");
        String schema = "interviewcopilot_p1_t06_it_" + UUID.randomUUID().toString().replace("-", "");

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
            UserRepository users = context.getBean(UserRepository.class);
            IdempotencyService service = context.getBean(IdempotencyService.class);
            UUID firstUser = UUID.randomUUID();
            UUID secondUser = UUID.randomUUID();
            users.insert(firstUser, "IdempotencyOwner", "test-hash", BASE_TIME);
            users.insert(secondUser, "IdempotencyOther", "test-hash", BASE_TIME);

            assertConcurrentClaimsAreAtomic(context, firstUser);

            Map<String, Object> request = Map.of("target_position", "Java Developer", "question_count", 5);
            IdempotencyDecision created = service.begin(
                    firstUser, IdempotencyOperation.CREATE_INTERVIEW, "CaseKey-001", request);
            assertEquals(IdempotencyDecision.Type.PROCEED, created.type());

            ApiException inProgress = assertThrows(ApiException.class, () -> service.begin(
                    firstUser, IdempotencyOperation.CREATE_INTERVIEW, "CaseKey-001", request));
            assertEquals(ApiErrorCode.OPERATION_IN_PROGRESS, inProgress.code());

            ApiException reused = assertThrows(ApiException.class, () -> service.begin(
                    firstUser, IdempotencyOperation.CREATE_INTERVIEW, "CaseKey-001",
                    Map.of("target_position", "Python Developer", "question_count", 5)));
            assertEquals(ApiErrorCode.IDEMPOTENCY_KEY_REUSED, reused.code());

            UUID resourceId = UUID.randomUUID();
            service.complete(created.recordId(), resourceId);
            IdempotencyDecision replay = service.begin(
                    firstUser, IdempotencyOperation.CREATE_INTERVIEW, "CaseKey-001", request);
            assertEquals(IdempotencyDecision.Type.REPLAY, replay.type());
            assertEquals(resourceId, replay.resourceId().orElseThrow());
            assertEquals(created.recordId(), replay.recordId());

            IdempotencyDecision caseSensitiveKey = service.begin(
                    firstUser, IdempotencyOperation.CREATE_INTERVIEW, "caseKey-001", request);
            IdempotencyDecision operationScoped = service.begin(
                    firstUser, IdempotencyOperation.START_INTERVIEW, "CaseKey-001", request);
            IdempotencyDecision userScoped = service.begin(
                    secondUser, IdempotencyOperation.CREATE_INTERVIEW, "CaseKey-001", request);
            assertNotEquals(created.recordId(), caseSensitiveKey.recordId());
            assertNotEquals(created.recordId(), operationScoped.recordId());
            assertNotEquals(created.recordId(), userScoped.recordId());

            service.abandon(caseSensitiveKey.recordId());
            IdempotencyDecision reclaimed = service.begin(
                    firstUser, IdempotencyOperation.CREATE_INTERVIEW, "caseKey-001", request);
            assertNotEquals(caseSensitiveKey.recordId(), reclaimed.recordId());
        }
    }

    private void assertConcurrentClaimsAreAtomic(ConfigurableApplicationContext context, UUID userId) {
        IdempotencyRepository repository = context.getBean(IdempotencyRepository.class);
        IdempotencyKey key = new IdempotencyKey("concurrent-claim-001");
        String hash = "a".repeat(64);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<IdempotencyClaim> first = executor.submit(() -> {
                start.await();
                return repository.claim(UUID.randomUUID(), userId, IdempotencyOperation.START_INTERVIEW,
                        key, hash, BASE_TIME);
            });
            Future<IdempotencyClaim> second = executor.submit(() -> {
                start.await();
                return repository.claim(UUID.randomUUID(), userId, IdempotencyOperation.START_INTERVIEW,
                        key, hash, BASE_TIME);
            });

            IdempotencyClaim firstClaim = first.get();
            IdempotencyClaim secondClaim = second.get();
            assertEquals(1, (firstClaim.created() ? 1 : 0) + (secondClaim.created() ? 1 : 0));
            assertEquals(firstClaim.record().id(), secondClaim.record().id());
        } catch (Exception exception) {
            throw new AssertionError("Concurrent idempotency claims must resolve to one record", exception);
        } finally {
            executor.shutdownNow();
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
                        new MapPropertySource("idempotency-persistence-test", Map.of(
                                "spring.datasource.url", jdbcUrl,
                                "spring.datasource.username", user,
                                "spring.datasource.password", password,
                                "interviewcopilot.ai-service.service-token", "idempotency-test-token",
                                "interviewcopilot.security.jwt.signing-secret",
                                "idempotency-test-jwt-signing-key-32-bytes"
                        ))))
                .run();
    }
}
