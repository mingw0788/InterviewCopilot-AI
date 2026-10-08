package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.interview.domain.ReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "interview_reports")
class InterviewReportEntity implements Persistable<UUID> {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    InterviewSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    ReportStatus status;

    @Column(name = "interview_overall_score", nullable = false, precision = 5, scale = 2)
    BigDecimal interviewOverallScore;

    @Column(name = "question_count", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    int questionCount;

    @Column(name = "completed_question_count", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    int completedQuestionCount;

    @Column(name = "average_accuracy", nullable = false, precision = 5, scale = 2)
    BigDecimal averageAccuracy;

    @Column(name = "average_completeness", nullable = false, precision = 5, scale = 2)
    BigDecimal averageCompleteness;

    @Column(name = "average_depth", nullable = false, precision = 5, scale = 2)
    BigDecimal averageDepth;

    @Column(name = "average_clarity", nullable = false, precision = 5, scale = 2)
    BigDecimal averageClarity;

    @Column(name = "duration_seconds", nullable = false)
    long durationSeconds;

    @Column(name = "strength_summary", columnDefinition = "TEXT")
    String strengthSummary;

    @Column(name = "weakness_summary", columnDefinition = "TEXT")
    String weaknessSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvement_suggestions", columnDefinition = "JSON")
    List<String> improvementSuggestions;

    @Column(name = "overall_comment", columnDefinition = "TEXT")
    String overallComment;

    @Column(name = "llm_provider", length = 100)
    String provider;

    @Column(name = "model_name", length = 200)
    String modelName;

    @Column(name = "prompt_version", length = 40)
    String promptVersion;

    @Column(name = "report_version", length = 40)
    String reportVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_usage", columnDefinition = "JSON")
    Map<String, Long> tokenUsage;

    @Column(name = "latency_ms")
    Long latencyMillis;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "published_at")
    Instant publishedAt;

    protected InterviewReportEntity() {
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
