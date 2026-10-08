package com.interviewcopilot.business.idempotency;

import java.time.Instant;
import java.util.UUID;

public interface IdempotencyRepository {
    IdempotencyClaim claim(
            UUID id,
            UUID userId,
            IdempotencyOperation operation,
            IdempotencyKey key,
            String requestHash,
            Instant now
    );

    boolean complete(UUID recordId, UUID resourceId, Instant now);

    boolean abandon(UUID recordId);
}
