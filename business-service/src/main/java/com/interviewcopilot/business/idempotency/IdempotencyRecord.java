package com.interviewcopilot.business.idempotency;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record IdempotencyRecord(
        UUID id,
        UUID userId,
        IdempotencyOperation operation,
        IdempotencyKey key,
        String requestHash,
        IdempotencyStatus status,
        Optional<UUID> resourceId,
        Instant createdAt,
        Instant updatedAt
) {
    public IdempotencyRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(operation, "operation is required");
        Objects.requireNonNull(key, "key is required");
        Objects.requireNonNull(status, "status is required");
        resourceId = Objects.requireNonNull(resourceId, "resourceId is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (requestHash == null || !requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestHash must be a lowercase SHA-256 hash");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (status == IdempotencyStatus.IN_PROGRESS && resourceId.isPresent()) {
            throw new IllegalArgumentException("An in-progress operation must not have a resourceId");
        }
        if (status == IdempotencyStatus.COMPLETED && resourceId.isEmpty()) {
            throw new IllegalArgumentException("A completed operation must have a resourceId");
        }
    }
}
