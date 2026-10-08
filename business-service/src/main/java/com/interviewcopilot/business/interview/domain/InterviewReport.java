package com.interviewcopilot.business.interview.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class InterviewReport {
    private final UUID id;
    private final UUID sessionId;
    private final InterviewMetrics metrics;
    private final Instant createdAt;
    private ReportStatus status;
    private ReportSummary summary;
    private AiCallMetadata generationMetadata;
    private String reportVersion;
    private Instant publishedAt;
    private Long persistenceVersion;

    private InterviewReport(UUID id, InterviewSession completedSession, Instant createdAt) {
        this.id = DomainChecks.required(id, "id");
        InterviewSession session = DomainChecks.required(completedSession, "completedSession");
        if (session.status() != InterviewStatus.COMPLETED) {
            throw new DomainRuleViolationException("A report can be created only for a completed interview");
        }
        this.sessionId = session.id();
        this.metrics = session.metrics().orElseThrow(
                () -> new DomainRuleViolationException("Completed interview metrics are required"));
        this.createdAt = DomainChecks.notBefore(
                createdAt,
                session.completedAt().orElseThrow(
                        () -> new DomainRuleViolationException("Completed interview timestamp is required")),
                "createdAt");
        this.status = ReportStatus.PENDING;
    }

    public static InterviewReport pending(
            UUID id,
            InterviewSession completedSession,
            Instant createdAt
    ) {
        return new InterviewReport(id, completedSession, createdAt);
    }

    public void beginGeneration() {
        if (status != ReportStatus.PENDING && status != ReportStatus.FAILED_RETRYABLE) {
            throw new DomainRuleViolationException("Report in status " + status + " cannot begin generation");
        }
        status = ReportStatus.GENERATING;
    }

    public void markGenerationFailed() {
        if (status != ReportStatus.GENERATING) {
            throw new DomainRuleViolationException("Only a generating report can record a retryable failure");
        }
        status = ReportStatus.FAILED_RETRYABLE;
    }

    public void publish(
            ReportSummary summary,
            AiCallMetadata generationMetadata,
            String reportVersion,
            Instant publishedAt
    ) {
        if (status != ReportStatus.GENERATING) {
            throw new DomainRuleViolationException("Only a generating report can be published");
        }
        ReportSummary validatedSummary = DomainChecks.required(summary, "summary");
        AiCallMetadata validatedMetadata = DomainChecks.required(generationMetadata, "generationMetadata");
        String validatedReportVersion = DomainChecks.requiredText(reportVersion, "reportVersion", 40);
        Instant validatedPublishedAt = DomainChecks.notBefore(publishedAt, createdAt, "publishedAt");
        this.summary = validatedSummary;
        this.generationMetadata = validatedMetadata;
        this.reportVersion = validatedReportVersion;
        this.publishedAt = validatedPublishedAt;
        status = ReportStatus.AVAILABLE;
    }

    public static InterviewReport reconstitute(
            UUID id,
            InterviewSession completedSession,
            ReportStatus status,
            InterviewMetrics persistedMetrics,
            ReportSummary summary,
            AiCallMetadata generationMetadata,
            String reportVersion,
            Instant createdAt,
            Instant publishedAt,
            long persistenceVersion
    ) {
        DomainChecks.required(status, "status");
        DomainChecks.required(persistedMetrics, "persistedMetrics");
        if (persistenceVersion < 0) {
            throw new DomainValidationException("persistenceVersion must not be negative");
        }
        InterviewReport report = pending(id, completedSession, createdAt);
        if (!report.metrics.equals(persistedMetrics)) {
            throw new DomainValidationException("Persisted report metrics do not match the completed interview");
        }
        switch (status) {
            case PENDING -> requireNoGeneratedContent(summary, generationMetadata, reportVersion, publishedAt, status);
            case GENERATING -> {
                requireNoGeneratedContent(summary, generationMetadata, reportVersion, publishedAt, status);
                report.beginGeneration();
            }
            case FAILED_RETRYABLE -> {
                requireNoGeneratedContent(summary, generationMetadata, reportVersion, publishedAt, status);
                report.beginGeneration();
                report.markGenerationFailed();
            }
            case AVAILABLE -> {
                report.beginGeneration();
                report.publish(summary, generationMetadata, reportVersion, publishedAt);
            }
        }
        report.persistenceVersion = persistenceVersion;
        return report;
    }

    private static void requireNoGeneratedContent(
            ReportSummary summary,
            AiCallMetadata generationMetadata,
            String reportVersion,
            Instant publishedAt,
            ReportStatus status
    ) {
        if (summary != null || generationMetadata != null || reportVersion != null || publishedAt != null) {
            throw new DomainValidationException("Generated report content must be absent for status " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public InterviewMetrics metrics() {
        return metrics;
    }

    public ReportStatus status() {
        return status;
    }

    public Optional<ReportSummary> summary() {
        return Optional.ofNullable(summary);
    }

    public Optional<AiCallMetadata> generationMetadata() {
        return Optional.ofNullable(generationMetadata);
    }

    public Optional<String> reportVersion() {
        return Optional.ofNullable(reportVersion);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public OptionalLong persistenceVersion() {
        return persistenceVersion == null ? OptionalLong.empty() : OptionalLong.of(persistenceVersion);
    }
}
