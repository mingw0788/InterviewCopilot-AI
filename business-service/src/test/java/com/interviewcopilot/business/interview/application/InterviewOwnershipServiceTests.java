package com.interviewcopilot.business.interview.application;

import com.interviewcopilot.business.interview.domain.Difficulty;
import com.interviewcopilot.business.interview.domain.InterviewConfiguration;
import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.repository.InterviewSessionRepository;
import com.interviewcopilot.business.web.ApiErrorCode;
import com.interviewcopilot.business.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewOwnershipServiceTests {
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewOwnershipService service = new InterviewOwnershipService(sessions);
    private final UUID interviewId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Test
    void returnsOnlyAnInterviewOwnedByTheAuthenticatedUser() {
        InterviewSession session = InterviewSession.create(
                interviewId,
                ownerId,
                new InterviewConfiguration("Java Developer", List.of("Java"), Difficulty.MEDIUM, 3),
                Instant.parse("2026-10-08T00:00:00Z"));
        when(sessions.findByIdAndUserId(interviewId, ownerId)).thenReturn(Optional.of(session));

        assertSame(session, service.requireOwned(interviewId, ownerId));
        verify(sessions, never()).existsById(interviewId);
    }

    @Test
    void distinguishesForbiddenOwnershipFromMissingResourcesWithoutReturningForeignData() {
        when(sessions.findByIdAndUserId(interviewId, ownerId)).thenReturn(Optional.empty());
        when(sessions.existsById(interviewId)).thenReturn(true);

        ApiException error = assertThrows(
                ApiException.class, () -> service.requireOwned(interviewId, ownerId));

        assertEquals(ApiErrorCode.FORBIDDEN, error.code());
        assertEquals(interviewId, error.details().get("interview_id"));
        verify(sessions, never()).findById(interviewId);
    }

    @Test
    void mapsAnUnknownInterviewToTheFrozenNotFoundCode() {
        when(sessions.findByIdAndUserId(interviewId, ownerId)).thenReturn(Optional.empty());
        when(sessions.existsById(interviewId)).thenReturn(false);

        ApiException error = assertThrows(
                ApiException.class, () -> service.requireOwned(interviewId, ownerId));

        assertEquals(ApiErrorCode.RESOURCE_NOT_FOUND, error.code());
    }
}
