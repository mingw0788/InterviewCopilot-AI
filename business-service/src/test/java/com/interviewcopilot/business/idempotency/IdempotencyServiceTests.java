package com.interviewcopilot.business.idempotency;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.interviewcopilot.business.web.ApiErrorCode;
import com.interviewcopilot.business.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-08T01:00:00Z");
    private final IdempotencyRepository repository = mock(IdempotencyRepository.class);
    private final IdempotencyRequestHasher hasher =
            new IdempotencyRequestHasher(JsonMapper.builder().build());
    private final IdempotencyService service = new IdempotencyService(
            repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void aNewClaimAllowsTheCallerToProceed() {
        String hash = hasher.hash(Map.of("interview_id", "one"));
        IdempotencyRecord record = record(hash, IdempotencyStatus.IN_PROGRESS, Optional.empty());
        when(repository.claim(any(), eq(userId), eq(IdempotencyOperation.START_INTERVIEW),
                eq(new IdempotencyKey("operation-001")), eq(hash), eq(NOW)))
                .thenReturn(new IdempotencyClaim(record, true));

        IdempotencyDecision decision = service.begin(
                userId, IdempotencyOperation.START_INTERVIEW, "operation-001",
                Map.of("interview_id", "one"));

        assertEquals(IdempotencyDecision.Type.PROCEED, decision.type());
        assertEquals(record.id(), decision.recordId());
        assertEquals(Optional.empty(), decision.resourceId());
    }

    @Test
    void aCompletedMatchingClaimReturnsTheOriginalResource() {
        UUID resourceId = UUID.randomUUID();
        String hash = hasher.hash(Map.of("answer", "text"));
        IdempotencyRecord record = record(hash, IdempotencyStatus.COMPLETED, Optional.of(resourceId));
        when(repository.claim(any(), eq(userId), eq(IdempotencyOperation.SUBMIT_ANSWER),
                any(), eq(hash), eq(NOW))).thenReturn(new IdempotencyClaim(record, false));

        IdempotencyDecision decision = service.begin(
                userId, IdempotencyOperation.SUBMIT_ANSWER, "operation-002", Map.of("answer", "text"));

        assertEquals(IdempotencyDecision.Type.REPLAY, decision.type());
        assertEquals(Optional.of(resourceId), decision.resourceId());
    }

    @Test
    void rejectsKeyReuseForADifferentRequestBeforeConsideringStatus() {
        IdempotencyRecord record = record("0".repeat(64), IdempotencyStatus.IN_PROGRESS, Optional.empty());
        when(repository.claim(any(), eq(userId), any(), any(), any(), eq(NOW)))
                .thenReturn(new IdempotencyClaim(record, false));

        ApiException error = assertThrows(ApiException.class, () -> service.begin(
                userId, IdempotencyOperation.CREATE_INTERVIEW, "operation-003", Map.of("value", 1)));

        assertEquals(ApiErrorCode.IDEMPOTENCY_KEY_REUSED, error.code());
    }

    @Test
    void rejectsConcurrentReplayWhileTheOriginalOperationIsInProgress() {
        String hash = hasher.hash(Map.of("value", 1));
        IdempotencyRecord record = record(hash, IdempotencyStatus.IN_PROGRESS, Optional.empty());
        when(repository.claim(any(), eq(userId), any(), any(), eq(hash), eq(NOW)))
                .thenReturn(new IdempotencyClaim(record, false));

        ApiException error = assertThrows(ApiException.class, () -> service.begin(
                userId, IdempotencyOperation.CREATE_INTERVIEW, "operation-004", Map.of("value", 1)));

        assertEquals(ApiErrorCode.OPERATION_IN_PROGRESS, error.code());
    }

    @Test
    void validatesKeysAndSupportsCompletionAndAbandonment() {
        ApiException invalid = assertThrows(ApiException.class, () -> service.begin(
                userId, IdempotencyOperation.CREATE_INTERVIEW, "short", Map.of("value", 1)));
        assertEquals(ApiErrorCode.INVALID_REQUEST, invalid.code());

        UUID recordId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        when(repository.complete(recordId, resourceId, NOW)).thenReturn(true);
        when(repository.abandon(recordId)).thenReturn(true);

        service.complete(recordId, resourceId);
        service.abandon(recordId);

        verify(repository).complete(recordId, resourceId, NOW);
        verify(repository).abandon(recordId);
    }

    private IdempotencyRecord record(
            String hash,
            IdempotencyStatus status,
            Optional<UUID> resourceId
    ) {
        return new IdempotencyRecord(
                UUID.randomUUID(), userId, IdempotencyOperation.CREATE_INTERVIEW,
                new IdempotencyKey("operation-001"), hash, status, resourceId, NOW, NOW);
    }
}
