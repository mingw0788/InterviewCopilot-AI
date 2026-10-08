package com.interviewcopilot.business.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import com.interviewcopilot.business.persistence.BinaryUuid;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcIdempotencyRepository implements IdempotencyRepository {
    private final JdbcTemplate jdbcTemplate;

    JdbcIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public IdempotencyClaim claim(
            UUID id,
            UUID userId,
            IdempotencyOperation operation,
            IdempotencyKey key,
            String requestHash,
            Instant now
    ) {
        require(id, "id");
        require(userId, "userId");
        require(operation, "operation");
        require(key, "key");
        require(requestHash, "requestHash");
        require(now, "now");
        LocalDateTime timestamp = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        int inserted = jdbcTemplate.update("""
                INSERT IGNORE INTO idempotency_records
                    (id, user_id, operation, idempotency_key, request_hash, status, resource_id,
                     created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), UNHEX(REPLACE(?, '-', '')), ?, ?, ?, 'IN_PROGRESS', NULL, ?, ?)
                """, id.toString(), userId.toString(), operation.name(), key.value(), requestHash,
                timestamp, timestamp);

        IdempotencyRecord record = find(userId, operation, key).orElseThrow(() ->
                new IllegalStateException("Idempotency claim could not be persisted"));
        return new IdempotencyClaim(record, inserted == 1);
    }

    @Override
    @Transactional
    public boolean complete(UUID recordId, UUID resourceId, Instant now) {
        require(recordId, "recordId");
        require(resourceId, "resourceId");
        require(now, "now");
        return jdbcTemplate.update("""
                UPDATE idempotency_records
                SET status = 'COMPLETED', resource_id = UNHEX(REPLACE(?, '-', '')), updated_at = ?
                WHERE id = UNHEX(REPLACE(?, '-', '')) AND status = 'IN_PROGRESS' AND resource_id IS NULL
                """, resourceId.toString(), LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                recordId.toString()) == 1;
    }

    @Override
    @Transactional
    public boolean abandon(UUID recordId) {
        require(recordId, "recordId");
        return jdbcTemplate.update("""
                DELETE FROM idempotency_records
                WHERE id = UNHEX(REPLACE(?, '-', '')) AND status = 'IN_PROGRESS' AND resource_id IS NULL
                """, recordId.toString()) == 1;
    }

    private Optional<IdempotencyRecord> find(
            UUID userId,
            IdempotencyOperation operation,
            IdempotencyKey key
    ) {
        List<IdempotencyRecord> records = jdbcTemplate.query("""
                SELECT id,
                       user_id,
                       operation,
                       idempotency_key,
                       request_hash,
                       status,
                       resource_id,
                       created_at,
                       updated_at
                FROM idempotency_records
                WHERE user_id = UNHEX(REPLACE(?, '-', '')) AND operation = ? AND idempotency_key = ?
                """, (result, rowNumber) -> new IdempotencyRecord(
                BinaryUuid.fromBytes(result.getBytes("id")),
                BinaryUuid.fromBytes(result.getBytes("user_id")),
                IdempotencyOperation.valueOf(result.getString("operation")),
                new IdempotencyKey(result.getString("idempotency_key")),
                result.getString("request_hash"),
                IdempotencyStatus.valueOf(result.getString("status")),
                Optional.ofNullable(result.getBytes("resource_id")).map(BinaryUuid::fromBytes),
                result.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                result.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)
        ), userId.toString(), operation.name(), key.value());
        return records.stream().findFirst();
    }

    private void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
