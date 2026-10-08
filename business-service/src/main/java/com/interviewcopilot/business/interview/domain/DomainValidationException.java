package com.interviewcopilot.business.interview.domain;

public final class DomainValidationException extends IllegalArgumentException {
    public DomainValidationException(String message) {
        super(message);
    }
}
