package com.interviewcopilot.business.interview.domain;

public final class DomainRuleViolationException extends IllegalStateException {
    public DomainRuleViolationException(String message) {
        super(message);
    }
}
