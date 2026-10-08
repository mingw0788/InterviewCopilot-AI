package com.interviewcopilot.business.interview.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record AiCallMetadata(
        String provider,
        String modelName,
        String promptVersion,
        Map<String, Long> tokenUsage,
        Long latencyMillis
) {
    public AiCallMetadata {
        provider = DomainChecks.requiredText(provider, "provider", 100);
        modelName = DomainChecks.requiredText(modelName, "modelName", 200);
        promptVersion = DomainChecks.requiredText(promptVersion, "promptVersion", 40);
        tokenUsage = immutableTokenUsage(tokenUsage);
        if (latencyMillis != null && latencyMillis < 0) {
            throw new DomainValidationException("latencyMillis must not be negative");
        }
    }

    private static Map<String, Long> immutableTokenUsage(Map<String, Long> tokenUsage) {
        if (tokenUsage == null || tokenUsage.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> copy = new LinkedHashMap<>();
        tokenUsage.forEach((key, value) -> {
            String normalizedKey = DomainChecks.requiredText(key, "tokenUsage key", 100);
            if (value == null || value < 0) {
                throw new DomainValidationException("tokenUsage values must not be negative");
            }
            copy.put(normalizedKey, value);
        });
        return Map.copyOf(copy);
    }
}
