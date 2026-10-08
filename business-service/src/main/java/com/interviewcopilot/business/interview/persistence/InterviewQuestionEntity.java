package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.interview.domain.Difficulty;
import com.interviewcopilot.business.interview.domain.QuestionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interview_questions")
class InterviewQuestionEntity {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    InterviewSessionEntity session;

    @Column(name = "question_number", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    int number;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    String text;

    @Column(name = "topic", nullable = false, length = 200)
    String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 16)
    Difficulty difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_points", nullable = false, columnDefinition = "JSON")
    List<String> expectedPoints = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    QuestionType type;

    @Column(name = "llm_provider", nullable = false, length = 100)
    String provider;

    @Column(name = "model_name", nullable = false, length = 200)
    String modelName;

    @Column(name = "prompt_version", nullable = false, length = 40)
    String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_usage", columnDefinition = "JSON")
    Map<String, Long> tokenUsage;

    @Column(name = "latency_ms")
    Long latencyMillis;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    InterviewAnswerEntity answer;

    protected InterviewQuestionEntity() {
    }
}
