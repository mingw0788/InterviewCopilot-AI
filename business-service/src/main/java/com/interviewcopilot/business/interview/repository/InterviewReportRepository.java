package com.interviewcopilot.business.interview.repository;

import com.interviewcopilot.business.interview.domain.InterviewReport;

import java.util.Optional;
import java.util.UUID;

public interface InterviewReportRepository {
    InterviewReport save(InterviewReport report);

    Optional<InterviewReport> findBySessionId(UUID sessionId);
}
