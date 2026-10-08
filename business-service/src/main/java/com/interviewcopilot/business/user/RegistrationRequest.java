package com.interviewcopilot.business.user;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RegistrationRequest(
        @JsonProperty("login_identifier") String loginIdentifier,
        String password
) {
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignored) {
        throw new IllegalArgumentException("Unknown registration field");
    }

    @Override
    public String toString() {
        return "RegistrationRequest[redacted]";
    }
}
