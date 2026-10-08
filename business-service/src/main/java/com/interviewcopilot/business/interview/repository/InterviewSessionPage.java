package com.interviewcopilot.business.interview.repository;

import com.interviewcopilot.business.interview.domain.InterviewSession;

import java.util.List;

public record InterviewSessionPage(
        List<InterviewSession> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public InterviewSessionPage {
        content = List.copyOf(content);
    }
}
