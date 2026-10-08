package com.interviewcopilot.business.integration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("interviewcopilot.ai-service")
public record AiServiceProperties(
        URI baseUrl,
        String serviceToken,
        Duration connectTimeout,
        Duration readTimeout
) {

    public AiServiceProperties {
        if (baseUrl == null
                || baseUrl.getHost() == null
                || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("AI service base URL must be an absolute HTTP(S) URL.");
        }
        if (serviceToken == null
                || serviceToken.isBlank()
                || !serviceToken.equals(serviceToken.trim())
                || !serviceToken.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new IllegalArgumentException(
                    "AI service token is required and must contain visible ASCII characters only."
            );
        }
        requirePositive(connectTimeout, "AI service connect timeout");
        requirePositive(readTimeout, "AI service read timeout");
    }

    private static void requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
    }
}
