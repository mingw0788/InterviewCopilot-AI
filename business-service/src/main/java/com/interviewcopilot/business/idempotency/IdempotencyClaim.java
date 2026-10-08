package com.interviewcopilot.business.idempotency;

import java.util.Objects;

public record IdempotencyClaim(IdempotencyRecord record, boolean created) {
    public IdempotencyClaim {
        Objects.requireNonNull(record, "record is required");
        if (created && record.status() != IdempotencyStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("A new claim must be in progress");
        }
    }
}
