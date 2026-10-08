package com.interviewcopilot.business.idempotency;

public enum IdempotencyOperation {
    CREATE_INTERVIEW,
    START_INTERVIEW,
    SUBMIT_ANSWER,
    CANCEL_INTERVIEW,
    RETRY_INTERVIEW_REPORT
}
