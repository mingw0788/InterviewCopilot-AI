package com.interviewcopilot.business.integration.ai;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AiServicePropertiesTests {

    @Test
    void rejectsNonHttpBaseUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiServiceProperties(
                        URI.create("file:///tmp/ai-service"),
                        "service-token",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)
                )
        );
    }

    @Test
    void rejectsBlankServiceToken() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiServiceProperties(
                        URI.create("http://localhost:8000"),
                        " ",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)
                )
        );
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiServiceProperties(
                        URI.create("http://localhost:8000"),
                        "service-token",
                        Duration.ZERO,
                        Duration.ofSeconds(1)
                )
        );
    }
}
