package com.interviewcopilot.business.idempotency;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record IdempotencyDecision(UUID recordId, Type type, Optional<UUID> resourceId) {
    public enum Type {
        PROCEED,
        REPLAY
    }

    public IdempotencyDecision {
        Objects.requireNonNull(recordId, "recordId is required");
        Objects.requireNonNull(type, "type is required");
        resourceId = Objects.requireNonNull(resourceId, "resourceId is required");
        if (type == Type.PROCEED && resourceId.isPresent()) {
            throw new IllegalArgumentException("A new operation must not have a resourceId");
        }
        if (type == Type.REPLAY && resourceId.isEmpty()) {
            throw new IllegalArgumentException("A replay must have a resourceId");
        }
    }

    public static IdempotencyDecision proceed(UUID recordId) {
        return new IdempotencyDecision(recordId, Type.PROCEED, Optional.empty());
    }

    public static IdempotencyDecision replay(UUID recordId, UUID resourceId) {
        return new IdempotencyDecision(recordId, Type.REPLAY, Optional.of(resourceId));
    }
}
