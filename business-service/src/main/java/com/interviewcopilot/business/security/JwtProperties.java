package com.interviewcopilot.business.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties("interviewcopilot.security.jwt")
public record JwtProperties(
        String issuer,
        String signingSecret,
        Duration accessTokenTtl
) {
    private static final Duration FROZEN_ACCESS_TOKEN_TTL = Duration.ofMinutes(60);

    public JwtProperties {
        if (issuer == null || issuer.isBlank() || !issuer.equals(issuer.trim())) {
            throw new IllegalArgumentException("JWT issuer is required and must not have surrounding whitespace");
        }
        if (signingSecret == null
                || signingSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || !signingSecret.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new IllegalArgumentException(
                    "JWT signing secret must contain at least 32 visible ASCII characters");
        }
        if (!FROZEN_ACCESS_TOKEN_TTL.equals(accessTokenTtl)) {
            throw new IllegalArgumentException("JWT access-token TTL must be exactly 60 minutes");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", signingSecret=<redacted>, accessTokenTtl="
                + accessTokenTtl + "]";
    }
}
