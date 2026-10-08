package com.interviewcopilot.business.auth;

public class InvalidLoginRequestException extends RuntimeException {
    public InvalidLoginRequestException(String message) {
        super(message);
    }
}
