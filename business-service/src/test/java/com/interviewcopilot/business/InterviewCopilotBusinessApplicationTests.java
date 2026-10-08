package com.interviewcopilot.business;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "MYSQL_HOST=configuration-test-db",
                "MYSQL_PORT=3307",
                "MYSQL_DATABASE=configuration_test",
                "MYSQL_USER=configuration_test_user",
                "MYSQL_PASSWORD=configuration-test-password",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
                "AI_SERVICE_BASE_URL=http://configuration-test-ai:8000",
                "INTERNAL_AI_SERVICE_TOKEN=configuration-test-service-token",
                "JWT_SIGNING_SECRET=configuration-test-jwt-key-32-bytes"
        }
)
@ActiveProfiles("local")
class InterviewCopilotBusinessApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Environment environment;

    @Test
    void applicationStartsOnRandomPort() {
        assertTrue(port > 0);
    }

    @Test
    void runtimeConfigurationLoadsEnvironmentValues() {
        assertEquals(
                "jdbc:mysql://configuration-test-db:3307/configuration_test"
                        + "?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                environment.getRequiredProperty("spring.datasource.url")
        );
        assertEquals(
                "http://configuration-test-ai:8000",
                environment.getRequiredProperty("interviewcopilot.ai-service.base-url")
        );
    }

    @Test
    void livenessEndpointReportsUpWithoutCheckingExternalDependencies() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/actuator/health/liveness",
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("status", "UP"), response.getBody());
    }

    @Test
    void currentUserRequiresBearerAuthentication() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/users/me", Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHORIZED", response.getBody().get("code"));
        assertEquals("Bearer", response.getHeaders().getFirst("WWW-Authenticate"));
    }
}
