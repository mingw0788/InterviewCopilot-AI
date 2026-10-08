package com.interviewcopilot.business.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(
        @JsonProperty("login_identifier") String loginIdentifier,
        String password
) {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignored) {
        throw new IllegalArgumentException("Unknown login field");
    }

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
