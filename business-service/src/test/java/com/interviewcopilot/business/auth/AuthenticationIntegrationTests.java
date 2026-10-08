package com.interviewcopilot.business.auth;

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
import org.testcontainers.containers.MySQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_RUN", matches = "true")
class AuthenticationIntegrationTests {
    private static final String JWT_TEST_SECRET = "authentication-integration-jwt-key-32-bytes";

    @Test
    void loginThenCurrentUserEnforcesJwtAndLiveUserStatus() throws Exception {
        String localBaseUrl = System.getenv("MIGRATION_TEST_JDBC_BASE_URL");
        if (localBaseUrl != null) {
            assertTrue(localBaseUrl.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/$"),
                    "Local authentication tests require loopback MySQL and no selected schema");
            String user = System.getenv().getOrDefault("MIGRATION_TEST_DB_USER", "root");
            String password = System.getenv().getOrDefault("MIGRATION_TEST_DB_PASSWORD", "");
            String schema = "interviewcopilot_p1_t03_it_" + UUID.randomUUID().toString().replace("-", "");
            try (Connection admin = DriverManager.getConnection(localBaseUrl, user, password);
                 Statement statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
                try {
                    verifyAuthentication(localBaseUrl + schema, user, password);
                } finally {
                    statement.execute("DROP DATABASE `" + schema + "`");
                }
            }
            return;
        }

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11")
                .withDatabaseName("interviewcopilot_p1_t03_test")
                .withUsername("authentication_test")
                .withPassword("authentication_test")) {
            mysql.start();
            verifyAuthentication(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }

    private void verifyAuthentication(String jdbcUrl, String user, String password) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                InterviewCopilotBusinessApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(application -> application.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("authentication-test", Map.of(
                                "spring.datasource.url", jdbcUrl,
                                "spring.datasource.username", user,
                                "spring.datasource.password", password,
                                "server.port", "0",
                                "interviewcopilot.ai-service.service-token", "authentication-test-token",
                                "interviewcopilot.security.jwt.signing-secret", JWT_TEST_SECRET
                        ))))
                .run()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            URI baseUri = URI.create("http://127.0.0.1:" + port + "/api/v1");
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpResponse<String> registered = post(client, baseUri.resolve("/api/v1/auth/register"), """
                        {"login_identifier":"Candidate01","password":"secure-password"}
                        """);
                assertEquals(201, registered.statusCode());
                String userId = mapper.readTree(registered.body()).path("data").path("user").path("id").asText();

                HttpResponse<String> missingCredentials = get(
                        client, baseUri.resolve("/api/v1/users/me"), null);
                assertUnauthorized(mapper, missingCredentials);

                HttpResponse<String> unknownUser = post(client, baseUri.resolve("/api/v1/auth/login"), """
                        {"login_identifier":"unknown-user","password":"secure-password"}
                        """);
                HttpResponse<String> wrongPassword = post(client, baseUri.resolve("/api/v1/auth/login"), """
                        {"login_identifier":"candidate01","password":"wrong-password"}
                        """);
                assertEquals(401, unknownUser.statusCode());
                assertEquals(401, wrongPassword.statusCode());
                assertEquals(
                        mapper.readTree(unknownUser.body()).path("message").asText(),
                        mapper.readTree(wrongPassword.body()).path("message").asText());

                HttpResponse<String> loggedIn = post(client, baseUri.resolve("/api/v1/auth/login"), """
                        {"login_identifier":"  candidate01  ","password":"secure-password"}
                        """);
                assertEquals(200, loggedIn.statusCode());
                JsonNode loginBody = mapper.readTree(loggedIn.body());
                String accessToken = loginBody.path("data").path("access_token").asText();
                assertFalse(accessToken.isBlank());
                assertEquals("Bearer", loginBody.path("data").path("token_type").asText());
                assertEquals(3600, loginBody.path("data").path("expires_in").asInt());
                assertEquals(userId, loginBody.path("data").path("user").path("id").asText());
                assertEquals("Candidate01",
                        loginBody.path("data").path("user").path("login_identifier").asText());
                assertFalse(loggedIn.body().contains("password"));

                HttpResponse<String> currentUser = get(
                        client, baseUri.resolve("/api/v1/users/me"), accessToken);
                assertEquals(200, currentUser.statusCode());
                JsonNode currentUserBody = mapper.readTree(currentUser.body());
                assertEquals(userId, currentUserBody.path("data").path("id").asText());
                assertEquals("Candidate01", currentUserBody.path("data").path("login_identifier").asText());
                assertEquals("ACTIVE", currentUserBody.path("data").path("status").asText());
                assertTrue(currentUserBody.path("data").hasNonNull("created_at"));
                assertFalse(currentUser.body().contains(accessToken));

                HttpResponse<String> invalidSignature = get(
                        client, baseUri.resolve("/api/v1/users/me"), accessToken + "tampered");
                assertUnauthorized(mapper, invalidSignature);
                assertFalse(invalidSignature.body().contains(accessToken));

                lockUser(jdbcUrl, user, password, userId);
                HttpResponse<String> lockedToken = get(
                        client, baseUri.resolve("/api/v1/users/me"), accessToken);
                assertUnauthorized(mapper, lockedToken);

                HttpResponse<String> lockedLogin = post(client, baseUri.resolve("/api/v1/auth/login"), """
                        {"login_identifier":"candidate01","password":"secure-password"}
                        """);
                assertEquals(401, lockedLogin.statusCode());
                assertEquals("Invalid login identifier or password",
                        mapper.readTree(lockedLogin.body()).path("message").asText());
            }
        }
    }

    private void lockUser(String jdbcUrl, String user, String password, String userId) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE users SET status = 'LOCKED', updated_at = UTC_TIMESTAMP(6) WHERE id = UNHEX(REPLACE(?, '-', ''))")) {
            statement.setString(1, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void assertUnauthorized(ObjectMapper mapper, HttpResponse<String> response) throws Exception {
        assertEquals(401, response.statusCode());
        assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElseThrow());
        JsonNode body = mapper.readTree(response.body());
        assertEquals("UNAUTHORIZED", body.path("code").asText());
        assertTrue(body.hasNonNull("request_id"));
        assertTrue(body.path("details").isObject());
    }

    private HttpResponse<String> post(HttpClient client, URI endpoint, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint)
                        .header("Content-Type", "application/json")
                        .header("X-Request-Id", "authentication-request")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, URI endpoint, String accessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .header("X-Request-Id", "authentication-request")
                .GET();
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
