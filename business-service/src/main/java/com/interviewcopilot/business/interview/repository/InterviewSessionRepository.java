package com.interviewcopilot.business.interview.repository;

import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.domain.InterviewStatus;

import java.util.Optional;
import java.util.UUID;

public interface InterviewSessionRepository {
    InterviewSession save(InterviewSession session);

    Optional<InterviewSession> findById(UUID id);

    Optional<InterviewSession> findByIdAndUserId(UUID id, UUID userId);

    boolean existsById(UUID id);

    InterviewSessionPage findPageByUserId(
            UUID userId,
            Optional<InterviewStatus> status,
            int page,
            int size
    );
}
