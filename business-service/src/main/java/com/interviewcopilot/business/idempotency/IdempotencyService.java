package com.interviewcopilot.business.idempotency;

import com.interviewcopilot.business.web.ApiErrorCode;
import com.interviewcopilot.business.web.ApiException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class IdempotencyService {
    private final IdempotencyRepository repository;
    private final IdempotencyRequestHasher hasher;
    private final Clock clock;

    public IdempotencyService(
            IdempotencyRepository repository,
            IdempotencyRequestHasher hasher,
            Clock clock
    ) {
        this.repository = repository;
        this.hasher = hasher;
        this.clock = clock;
    }

    public IdempotencyDecision begin(
            UUID userId,
            IdempotencyOperation operation,
            String rawKey,
            Object requestIdentity
    ) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(operation, "operation is required");
        IdempotencyKey key;
        try {
            key = new IdempotencyKey(rawKey);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, exception.getMessage());
        }

        String requestHash;
        try {
            requestHash = hasher.hash(requestIdentity);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Unable to identify the idempotent request");
        }

        Instant now = clock.instant();
        IdempotencyClaim claim = repository.claim(
                UUID.randomUUID(), userId, operation, key, requestHash, now);
        IdempotencyRecord record = claim.record();
        if (!record.requestHash().equals(requestHash)) {
            throw new ApiException(
                    ApiErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used with a different request",
                    Map.of("operation", operation.name()));
        }
        if (claim.created()) {
            return IdempotencyDecision.proceed(record.id());
        }
        if (record.status() == IdempotencyStatus.IN_PROGRESS) {
            throw new ApiException(
                    ApiErrorCode.OPERATION_IN_PROGRESS,
                    "An operation with this Idempotency-Key is still in progress",
                    Map.of("operation", operation.name()));
        }
        return IdempotencyDecision.replay(record.id(), record.resourceId().orElseThrow());
    }

    public void complete(UUID recordId, UUID resourceId) {
        Objects.requireNonNull(recordId, "recordId is required");
        Objects.requireNonNull(resourceId, "resourceId is required");
        if (!repository.complete(recordId, resourceId, clock.instant())) {
            throw new IllegalStateException("Idempotency record is not in progress");
        }
    }

    public void abandon(UUID recordId) {
        Objects.requireNonNull(recordId, "recordId is required");
        if (!repository.abandon(recordId)) {
            throw new IllegalStateException("Idempotency record is not in progress");
        }
    }
}
