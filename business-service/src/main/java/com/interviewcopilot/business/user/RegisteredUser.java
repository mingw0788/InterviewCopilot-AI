package com.interviewcopilot.business.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record RegisteredUser(
        UUID id,
        @JsonProperty("login_identifier") String loginIdentifier,
        UserStatus status,
        @JsonProperty("created_at") Instant createdAt
) {
}
