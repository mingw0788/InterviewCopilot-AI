package com.interviewcopilot.business.persistence;

import com.interviewcopilot.business.InterviewCopilotBusinessApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_RUN", matches = "true")
class DatabaseMigrationIntegrationTests {

    @Test
    void freshMySqlMigratesTwiceAndEnforcesOwnershipAndUniqueness() throws SQLException {
        String localBaseUrl = System.getenv("MIGRATION_TEST_JDBC_BASE_URL");
        if (localBaseUrl != null) {
            testAgainstIsolatedLocalSchema(localBaseUrl);
            return;
        }

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11")
                .withDatabaseName("interviewcopilot_p1_t01_test")
                .withUsername("migration_test")
                .withPassword("migration_test")) {
            mysql.start();
            verifySchema(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }

    private void testAgainstIsolatedLocalSchema(String baseUrl) throws SQLException {
        assertTrue(baseUrl.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/$"),
                "Local migration tests may only connect to loopback MySQL without a selected schema");
        String user = System.getenv().getOrDefault("MIGRATION_TEST_DB_USER", "root");
        String password = System.getenv().getOrDefault("MIGRATION_TEST_DB_PASSWORD", "");
        String schema = "interviewcopilot_p1_t01_it_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection admin = DriverManager.getConnection(baseUrl, user, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
            try {
                verifySchema(baseUrl + schema, user, password);
            } finally {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    private void verifySchema(String jdbcUrl, String user, String password) throws SQLException {
        startBusinessService(jdbcUrl, user, password);
        assertEquals(1, appliedMigrationCount(jdbcUrl, user, password));
        startBusinessService(jdbcUrl, user, password);
        assertEquals(1, appliedMigrationCount(jdbcUrl, user, password));

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            assertEquals(7, countCoreTables(connection));
            assertContractColumnLength(connection, "interview_questions", "topic", 200);
            for (String table : new String[] {
                    "interview_questions", "answer_evaluations", "interview_reports"
            }) {
                assertContractColumnLength(connection, table, "llm_provider", 100);
                assertContractColumnLength(connection, table, "model_name", 200);
            }
            verifyConstraints(connection);
        }
    }

    private void startBusinessService(String jdbcUrl, String user, String password) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                InterviewCopilotBusinessApplication.class)
                .web(WebApplicationType.NONE)
                .initializers(application -> application.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("migration-test", Map.of(
                                "spring.datasource.url", jdbcUrl,
                                "spring.datasource.username", user,
                                "spring.datasource.password", password,
                                "interviewcopilot.ai-service.service-token", "migration-test-token",
                                "interviewcopilot.security.jwt.signing-secret",
                                "migration-test-jwt-signing-key-32-bytes"
                        ))))
                .run()) {
            Flyway flyway = context.getBean(Flyway.class);
            assertEquals("1", flyway.info().current().getVersion().toString());
        }
    }

    private int appliedMigrationCount(String jdbcUrl, String user, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")) {
            result.next();
            return result.getInt(1);
        }
    }

    private int countCoreTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema = DATABASE()
                       AND table_name IN ('users', 'interview_sessions', 'interview_questions',
                                          'interview_answers', 'answer_evaluations',
                                          'interview_reports', 'idempotency_records')
                     """)) {
            result.next();
            return result.getInt(1);
        }
    }

    private void assertContractColumnLength(Connection connection, String table, String column, int expected)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Missing column " + table + "." + column);
                assertEquals(expected, result.getInt(1), "Contract length mismatch for " + table + "." + column);
            }
        }
    }

    private void verifyConstraints(Connection connection) throws SQLException {
        String userId = newId();
        execute(connection, """
                INSERT INTO users (id, login_identifier, password_hash, status, created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, 'test-hash', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, userId, "MigrationUser");
        assertMySqlError(1062, () -> execute(connection, """
                INSERT INTO users (id, login_identifier, password_hash, status, created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, 'test-hash', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, newId(), "migrationuser"));
        assertMySqlError(3819, () -> execute(connection, """
                INSERT INTO users (id, login_identifier, password_hash, status, created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, 'test-hash', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, newId(), " leadingUser"));
        assertMySqlError(3819, () -> execute(connection, """
                INSERT INTO users (id, login_identifier, password_hash, status, created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, 'test-hash', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, newId(), "trailingUser "));

        String sessionId = newId();
        insertSession(connection, sessionId, userId, 3);
        assertMySqlError(1452, () -> insertSession(connection, newId(), newId(), 3));
        assertMySqlError(3819, () -> insertSession(connection, newId(), userId, 2));
        assertEquals(0, versionOf(connection, "interview_sessions", sessionId));

        String questionId = newId();
        insertQuestion(connection, questionId, sessionId, 1);
        assertMySqlError(3819, () -> insertQuestion(connection, newId(), sessionId, 11));
        assertMySqlError(1062, () -> insertQuestion(connection, newId(), sessionId, 1));

        String answerId = newId();
        insertAnswer(connection, answerId, questionId);
        assertMySqlError(1062, () -> insertAnswer(connection, newId(), questionId));

        insertEvaluation(connection, newId(), answerId);
        assertMySqlError(1062, () -> insertEvaluation(connection, newId(), answerId));

        String reportId = newId();
        insertReport(connection, reportId, sessionId);
        assertMySqlError(1062, () -> insertReport(connection, newId(), sessionId));
        assertEquals(0, versionOf(connection, "interview_reports", reportId));
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT strength_summary, report_version FROM interview_reports")) {
            result.next();
            assertNull(result.getString("strength_summary"));
            assertNull(result.getString("report_version"));
        }

        insertIdempotencyRecord(connection, newId(), userId, "CreateInterview", "KeyABCDEF", sessionId);
        assertMySqlError(1062, () -> insertIdempotencyRecord(
                connection, newId(), userId, "CreateInterview", "KeyABCDEF", sessionId));
        insertIdempotencyRecord(connection, newId(), userId, "CreateInterview", "keyABCDEF", sessionId);
        insertIdempotencyRecord(connection, newId(), userId, "StartInterview", "KeyABCDEF", sessionId);
        assertMySqlError(3819, () -> insertIdempotencyRecord(
                connection, newId(), userId, "CreateInterview", "short", sessionId));
        assertMySqlError(1452, () -> insertIdempotencyRecord(
                connection, newId(), newId(), "CreateInterview", "KeyABCDEF", sessionId));
    }

    private void insertSession(Connection connection, String sessionId, String userId, int questionCount)
            throws SQLException {
        execute(connection, """
                INSERT INTO interview_sessions
                    (id, user_id, target_position, skills, difficulty, question_count, status, created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), UNHEX(REPLACE(?, '-', '')), 'Java Engineer', '["Java"]', 'MEDIUM', ?,
                        'CREATED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, sessionId, userId, questionCount);
    }

    private void insertQuestion(Connection connection, String questionId, String sessionId, int number)
            throws SQLException {
        execute(connection, """
                INSERT INTO interview_questions
                    (id, session_id, question_number, question_text, topic, difficulty, expected_points,
                     question_type, llm_provider, model_name, prompt_version, created_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), UNHEX(REPLACE(?, '-', '')), ?, 'Explain transactions', 'Transactions',
                        'MEDIUM', '["ACID"]', 'CONCEPTUAL', 'mock', 'mock-model', 'question-v1', UTC_TIMESTAMP(6))
                """, questionId, sessionId, number);
    }

    private void insertAnswer(Connection connection, String answerId, String questionId) throws SQLException {
        execute(connection, """
                INSERT INTO interview_answers (id, question_id, answer_content, submitted_at, created_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), UNHEX(REPLACE(?, '-', '')), 'ACID properties', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, answerId, questionId);
    }

    private void insertEvaluation(Connection connection, String evaluationId, String answerId)
            throws SQLException {
        execute(connection, """
                INSERT INTO answer_evaluations
                    (id, answer_id, accuracy, completeness, depth, clarity, answer_overall_score,
                     strengths, missing_points, feedback, llm_provider, model_name, prompt_version,
                     evaluation_version, created_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), UNHEX(REPLACE(?, '-', '')), 80.00, 80.00, 80.00, 80.00, 80.00,
                        '["Clear"]', '[]', 'Good answer', 'mock', 'mock-model', 'evaluation-prompt-v1',
                        'evaluation-v1', UTC_TIMESTAMP(6))
                """, evaluationId, answerId);
    }

    private void insertReport(Connection connection, String reportId, String sessionId) throws SQLException {
        execute(connection, """
                INSERT INTO interview_reports
                    (id, session_id, status, interview_overall_score, question_count,
                     completed_question_count, average_accuracy, average_completeness, average_depth,
                     average_clarity, duration_seconds, created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), UNHEX(REPLACE(?, '-', '')), 'PENDING', 80.00, 3, 3,
                        80.00, 80.00, 80.00, 80.00, 120, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, reportId, sessionId);
    }

    private void insertIdempotencyRecord(Connection connection, String id, String userId,
                                         String operation, String key, String resourceId) throws SQLException {
        execute(connection, """
                INSERT INTO idempotency_records
                    (id, user_id, operation, idempotency_key, request_hash, status, resource_id,
                     created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), UNHEX(REPLACE(?, '-', '')), ?, ?, ?, 'COMPLETED', UNHEX(REPLACE(?, '-', '')),
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id, userId, operation, key, "a".repeat(64), resourceId);
    }

    private long versionOf(Connection connection, String table, String id) throws SQLException {
        String sql = switch (table) {
            case "interview_sessions" -> "SELECT version FROM interview_sessions WHERE id = UNHEX(REPLACE(?, '-', ''))";
            case "interview_reports" -> "SELECT version FROM interview_reports WHERE id = UNHEX(REPLACE(?, '-', ''))";
            default -> throw new IllegalArgumentException("Unexpected versioned table");
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private void assertMySqlError(int expectedCode, Executable action) {
        SQLException exception = assertThrows(SQLException.class, action);
        if (expectedCode == 3819) {
            assertTrue(exception.getErrorCode() == 3819 || exception.getErrorCode() == 4025,
                    "Expected a MySQL or MariaDB CHECK constraint violation, got " + exception.getErrorCode());
        } else {
            assertEquals(expectedCode, exception.getErrorCode());
        }
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
