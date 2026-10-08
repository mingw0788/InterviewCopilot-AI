package com.interviewcopilot.business.interview.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_answers")
class InterviewAnswerEntity {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    InterviewQuestionEntity question;

    @Column(name = "answer_content", nullable = false, columnDefinition = "TEXT")
    String content;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @OneToOne(mappedBy = "answer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    AnswerEvaluationEntity evaluation;

    protected InterviewAnswerEntity() {
    }
}
