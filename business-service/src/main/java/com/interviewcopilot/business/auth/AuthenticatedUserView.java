package com.interviewcopilot.business.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interviewcopilot.business.user.UserStatus;

import java.util.UUID;

public record AuthenticatedUserView(
        UUID id,
        @JsonProperty("login_identifier") String loginIdentifier,
        UserStatus status
) {
}
