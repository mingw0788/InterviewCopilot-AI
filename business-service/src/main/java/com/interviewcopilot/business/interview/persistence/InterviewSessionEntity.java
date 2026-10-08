package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.interview.domain.Difficulty;
import com.interviewcopilot.business.interview.domain.InterviewStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "interview_sessions")
class InterviewSessionEntity implements Persistable<UUID> {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    UUID userId;

    @Column(name = "target_position", nullable = false, length = 100)
    String targetPosition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills", nullable = false, columnDefinition = "JSON")
    List<String> skills = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 16)
    Difficulty difficulty;

    @Column(name = "question_count", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    int questionCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    InterviewStatus status;

    @Column(name = "current_question_number", columnDefinition = "SMALLINT UNSIGNED")
    Integer currentQuestionNumber;

    @Column(name = "interview_overall_score", precision = 5, scale = 2)
    BigDecimal interviewOverallScore;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    @OrderBy("number ASC")
    List<InterviewQuestionEntity> questions = new ArrayList<>();

    protected InterviewSessionEntity() {
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return version == null;
    }
}
