package com.interviewcopilot.business.user;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String loginIdentifier,
        String passwordHash,
        UserStatus status,
        Instant createdAt
) {
    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", loginIdentifier=" + loginIdentifier
                + ", status=" + status + ", createdAt=" + createdAt + ", passwordHash=<redacted>]";
    }
}
