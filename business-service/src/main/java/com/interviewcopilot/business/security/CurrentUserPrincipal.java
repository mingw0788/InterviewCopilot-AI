package com.interviewcopilot.business.security;

import com.interviewcopilot.business.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record CurrentUserPrincipal(
        UUID id,
        String loginIdentifier,
        UserStatus status,
        Instant createdAt
) {
}
