package com.interviewcopilot.business.interview.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "answer_evaluations")
class AnswerEvaluationEntity {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false, unique = true)
    InterviewAnswerEntity answer;

    @Column(name = "accuracy", nullable = false, precision = 5, scale = 2)
    BigDecimal accuracy;

    @Column(name = "completeness", nullable = false, precision = 5, scale = 2)
    BigDecimal completeness;

    @Column(name = "depth", nullable = false, precision = 5, scale = 2)
    BigDecimal depth;

    @Column(name = "clarity", nullable = false, precision = 5, scale = 2)
    BigDecimal clarity;

    @Column(name = "answer_overall_score", nullable = false, precision = 5, scale = 2)
    BigDecimal answerOverallScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", nullable = false, columnDefinition = "JSON")
    List<String> strengths = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_points", nullable = false, columnDefinition = "JSON")
    List<String> missingPoints = new ArrayList<>();

    @Column(name = "feedback", nullable = false, columnDefinition = "TEXT")
    String feedback;

    @Column(name = "llm_provider", nullable = false, length = 100)
    String provider;

    @Column(name = "model_name", nullable = false, length = 200)
    String modelName;

    @Column(name = "prompt_version", nullable = false, length = 40)
    String promptVersion;

    @Column(name = "evaluation_version", nullable = false, length = 40)
    String evaluationVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_usage", columnDefinition = "JSON")
    Map<String, Long> tokenUsage;

    @Column(name = "latency_ms")
    Long latencyMillis;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    protected AnswerEvaluationEntity() {
    }
}
