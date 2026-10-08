package com.interviewcopilot.business.interview.application;

import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.repository.InterviewSessionRepository;
import com.interviewcopilot.business.web.ApiErrorCode;
import com.interviewcopilot.business.web.ApiException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class InterviewOwnershipService {
    private final InterviewSessionRepository sessions;

    public InterviewOwnershipService(InterviewSessionRepository sessions) {
        this.sessions = sessions;
    }

    public InterviewSession requireOwned(UUID interviewId, UUID userId) {
        Objects.requireNonNull(interviewId, "interviewId is required");
        Objects.requireNonNull(userId, "userId is required");

        return sessions.findByIdAndUserId(interviewId, userId).orElseThrow(() -> {
            if (sessions.existsById(interviewId)) {
                return new ApiException(
                        ApiErrorCode.FORBIDDEN,
                        "The interview belongs to another user",
                        Map.of("interview_id", interviewId));
            }
            return new ApiException(
                    ApiErrorCode.RESOURCE_NOT_FOUND,
                    "Interview not found",
                    Map.of("interview_id", interviewId));
        });
    }
}
