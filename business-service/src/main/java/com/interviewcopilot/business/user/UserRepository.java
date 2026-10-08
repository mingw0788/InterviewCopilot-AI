package com.interviewcopilot.business.user;

import org.springframework.jdbc.core.JdbcTemplate;
import com.interviewcopilot.business.persistence.BinaryUuid;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID id, String loginIdentifier, String passwordHash, Instant createdAt) {
        LocalDateTime utcCreatedAt = LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO users (id, login_identifier, password_hash, status, created_at, updated_at)
                VALUES (UNHEX(REPLACE(?, '-', '')), ?, ?, 'ACTIVE', ?, ?)
                """, id.toString(), loginIdentifier, passwordHash, utcCreatedAt, utcCreatedAt);
    }

    public Optional<UserAccount> findByLoginIdentifier(String loginIdentifier) {
        return query("""
                SELECT id, login_identifier, password_hash, status, created_at
                FROM users
                WHERE login_identifier = ?
                """, loginIdentifier);
    }

    public Optional<UserAccount> findById(UUID id) {
        return query("""
                SELECT id, login_identifier, password_hash, status, created_at
                FROM users
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """, id.toString());
    }

    private Optional<UserAccount> query(String sql, String parameter) {
        List<UserAccount> users = jdbcTemplate.query(sql, (result, rowNumber) -> new UserAccount(
                BinaryUuid.fromBytes(result.getBytes("id")),
                result.getString("login_identifier"),
                result.getString("password_hash"),
                UserStatus.valueOf(result.getString("status")),
                result.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)
        ), parameter);
        return users.stream().findFirst();
    }
}
