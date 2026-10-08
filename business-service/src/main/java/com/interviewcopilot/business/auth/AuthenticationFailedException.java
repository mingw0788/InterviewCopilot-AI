package com.interviewcopilot.business.auth;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Invalid login identifier or password");
    }
}
