package com.interviewcopilot.business.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewcopilot.business.InterviewCopilotBusinessApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.MySQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_RUN", matches = "true")
class RegistrationIntegrationTests {
    @Test
    void registerPersistsOnlyHashAndRejectsCaseInsensitiveDuplicate() throws Exception {
        String localBaseUrl = System.getenv("MIGRATION_TEST_JDBC_BASE_URL");
        if (localBaseUrl != null) {
            assertTrue(localBaseUrl.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/$"),
                    "Local registration tests require loopback MySQL and no selected schema");
            String user = System.getenv().getOrDefault("MIGRATION_TEST_DB_USER", "root");
            String password = System.getenv().getOrDefault("MIGRATION_TEST_DB_PASSWORD", "");
            String schema = "interviewcopilot_p1_t02_it_" + UUID.randomUUID().toString().replace("-", "");
            try (Connection admin = DriverManager.getConnection(localBaseUrl, user, password);
                 Statement statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
                try {
                    verifyRegistration(localBaseUrl + schema, user, password);
                } finally {
                    statement.execute("DROP DATABASE `" + schema + "`");
                }
            }
            return;
        }

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11")
                .withDatabaseName("interviewcopilot_p1_t02_test")
                .withUsername("registration_test")
                .withPassword("registration_test")) {
            mysql.start();
            verifyRegistration(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }

    private void verifyRegistration(String jdbcUrl, String user, String password) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                InterviewCopilotBusinessApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(application -> application.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("registration-test", Map.of(
                                "spring.datasource.url", jdbcUrl,
                                "spring.datasource.username", user,
                                "spring.datasource.password", password,
                                "server.port", "0",
                                "interviewcopilot.ai-service.service-token", "registration-test-token",
                                "interviewcopilot.security.jwt.signing-secret",
                                "registration-test-jwt-signing-key-32-bytes"
                        ))))
                .run()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            URI endpoint = URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register");
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpResponse<String> created = send(client, endpoint, """
                        {"login_identifier":"  Candidate01  ","password":"secure-password"}
                        """);
                assertEquals(201, created.statusCode());
                JsonNode createdBody = mapper.readTree(created.body());
                JsonNode registeredUser = createdBody.path("data").path("user");
                assertEquals("Candidate01", registeredUser.path("login_identifier").asText());
                assertEquals("ACTIVE", registeredUser.path("status").asText());
                assertTrue(registeredUser.hasNonNull("created_at"));
                assertTrue(createdBody.hasNonNull("timestamp"));
                assertEquals("registration-request", createdBody.path("request_id").asText());
                assertFalse(created.body().contains("password"));

                try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
                     Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery(
                             "SELECT id, login_identifier, password_hash FROM users")) {
                    assertTrue(result.next());
                    assertEquals(registeredUser.path("id").asText(), com.interviewcopilot.business.persistence.BinaryUuid.fromBytes(result.getBytes(1)).toString());
                    assertEquals("Candidate01", result.getString(2));
                    String hash = result.getString(3);
                    assertNotEquals("secure-password", hash);
                    assertTrue(hash.startsWith("$argon2id$"));
                    assertTrue(context.getBean(PasswordEncoder.class).matches("secure-password", hash));
                    assertFalse(result.next());
                }

                HttpResponse<String> duplicate = send(client, endpoint, """
                        {"login_identifier":"candidate01","password":"another-password"}
                        """);
                assertEquals(409, duplicate.statusCode());
                JsonNode conflict = mapper.readTree(duplicate.body());
                assertEquals("DUPLICATE_SUBMISSION", conflict.path("code").asText());
                assertEquals("registration-request", conflict.path("request_id").asText());
                assertTrue(conflict.path("details").isObject());
                assertFalse(duplicate.body().contains("another-password"));

                try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
                     Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }

                HttpResponse<String> invalid = send(client, endpoint, """
                        {"login_identifier":"another","password":"short"}
                        """);
                assertEquals(422, invalid.statusCode());
                assertEquals("VALIDATION_FAILED", mapper.readTree(invalid.body()).path("code").asText());
            }
        }
    }

    private HttpResponse<String> send(HttpClient client, URI endpoint, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint)
                        .header("Content-Type", "application/json")
                        .header("X-Request-Id", "registration-request")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
    }
}
