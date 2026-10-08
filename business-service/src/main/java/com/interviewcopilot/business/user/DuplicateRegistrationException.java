package com.interviewcopilot.business.user;

public class DuplicateRegistrationException extends RuntimeException {
    public DuplicateRegistrationException() {
        super("Login identifier is already in use");
    }
}
