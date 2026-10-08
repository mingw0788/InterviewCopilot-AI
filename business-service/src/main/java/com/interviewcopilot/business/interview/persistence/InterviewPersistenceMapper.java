package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.interview.domain.AiCallMetadata;
import com.interviewcopilot.business.interview.domain.AnswerEvaluation;
import com.interviewcopilot.business.interview.domain.InterviewAnswer;
import com.interviewcopilot.business.interview.domain.InterviewConfiguration;
import com.interviewcopilot.business.interview.domain.InterviewMetrics;
import com.interviewcopilot.business.interview.domain.InterviewQuestion;
import com.interviewcopilot.business.interview.domain.InterviewReport;
import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.domain.ReportSummary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class InterviewPersistenceMapper {
    private InterviewPersistenceMapper() {
    }

    static InterviewSessionEntity toEntity(InterviewSession source, Instant updatedAt) {
        InterviewSessionEntity target = new InterviewSessionEntity();
        target.id = source.id();
        target.userId = source.userId();
        target.targetPosition = source.configuration().targetPosition();
        target.skills = new ArrayList<>(source.configuration().skills());
        target.difficulty = source.configuration().difficulty();
        target.questionCount = source.configuration().questionCount();
        target.status = source.status();
        target.currentQuestionNumber = source.currentQuestionNumber().orElse(null);
        target.interviewOverallScore = source.interviewOverallScore().orElse(null);
        target.version = source.persistenceVersion().isPresent()
                ? source.persistenceVersion().getAsLong()
                : null;
        target.startedAt = source.startedAt().orElse(null);
        target.completedAt = source.completedAt().orElse(null);
        target.cancelledAt = source.cancelledAt().orElse(null);
        target.createdAt = source.createdAt();
        target.updatedAt = updatedAt;
        for (InterviewQuestion question : source.questions()) {
            target.questions.add(toEntity(question, target));
        }
        return target;
    }

    static InterviewSession toDomain(InterviewSessionEntity source) {
        List<InterviewQuestion> questions = source.questions.stream()
                .map(InterviewPersistenceMapper::toDomain)
                .toList();
        return InterviewSession.reconstitute(
                source.id,
                source.userId,
                new InterviewConfiguration(
                        source.targetPosition, source.skills, source.difficulty, source.questionCount),
                source.status,
                questions,
                source.currentQuestionNumber,
                source.interviewOverallScore,
                source.createdAt,
                source.startedAt,
                source.completedAt,
                source.cancelledAt,
                requiredVersion(source.version, "interview session"));
    }

    static InterviewReportEntity toEntity(
            InterviewReport source,
            InterviewSessionEntity sessionReference,
            Instant updatedAt
    ) {
        InterviewReportEntity target = new InterviewReportEntity();
        copyReportFields(source, target, sessionReference, updatedAt);
        target.version = source.persistenceVersion().isPresent()
                ? source.persistenceVersion().getAsLong()
                : null;
        return target;
    }

    static void copyReportFields(
            InterviewReport source,
            InterviewReportEntity target,
            InterviewSessionEntity sessionReference,
            Instant updatedAt
    ) {
        target.id = source.id();
        target.session = sessionReference;
        target.status = source.status();
        InterviewMetrics metrics = source.metrics();
        target.interviewOverallScore = metrics.interviewOverallScore();
        target.questionCount = metrics.questionCount();
        target.completedQuestionCount = metrics.completedQuestionCount();
        target.averageAccuracy = metrics.averageAccuracy();
        target.averageCompleteness = metrics.averageCompleteness();
        target.averageDepth = metrics.averageDepth();
        target.averageClarity = metrics.averageClarity();
        target.durationSeconds = metrics.durationSeconds();
        source.summary().ifPresent(summary -> {
            target.strengthSummary = summary.strengthSummary();
            target.weaknessSummary = summary.weaknessSummary();
            target.improvementSuggestions = new ArrayList<>(summary.improvementSuggestions());
            target.overallComment = summary.overallComment();
        });
        source.generationMetadata().ifPresent(metadata -> copyMetadata(metadata, target));
        target.reportVersion = source.reportVersion().orElse(null);
        target.createdAt = source.createdAt();
        target.updatedAt = updatedAt;
        target.publishedAt = source.publishedAt().orElse(null);
    }

    static InterviewReport toDomain(InterviewReportEntity source, InterviewSession completedSession) {
        ReportSummary summary = reportSummary(source);
        AiCallMetadata metadata = nullableMetadata(
                source.provider,
                source.modelName,
                source.promptVersion,
                source.tokenUsage,
                source.latencyMillis);
        return InterviewReport.reconstitute(
                source.id,
                completedSession,
                source.status,
                new InterviewMetrics(
                        source.interviewOverallScore,
                        source.questionCount,
                        source.completedQuestionCount,
                        source.averageAccuracy,
                        source.averageCompleteness,
                        source.averageDepth,
                        source.averageClarity,
                        source.durationSeconds),
                summary,
                metadata,
                source.reportVersion,
                source.createdAt,
                source.publishedAt,
                requiredVersion(source.version, "interview report"));
    }

    private static InterviewQuestionEntity toEntity(
            InterviewQuestion source,
            InterviewSessionEntity session
    ) {
        InterviewQuestionEntity target = new InterviewQuestionEntity();
        target.id = source.id();
        target.session = session;
        target.number = source.number();
        target.text = source.text();
        target.topic = source.topic();
        target.difficulty = source.difficulty();
        target.expectedPoints = new ArrayList<>(source.expectedPoints());
        target.type = source.type();
        copyMetadata(source.generationMetadata(), target);
        target.createdAt = source.createdAt();
        source.answer().ifPresent(answer -> target.answer = toEntity(answer, target));
        return target;
    }

    private static InterviewQuestion toDomain(InterviewQuestionEntity source) {
        InterviewAnswer answer = source.answer == null ? null : toDomain(source.answer);
        return InterviewQuestion.reconstitute(
                source.id,
                source.number,
                source.text,
                source.topic,
                source.difficulty,
                source.expectedPoints,
                source.type,
                requiredMetadata(
                        source.provider,
                        source.modelName,
                        source.promptVersion,
                        source.tokenUsage,
                        source.latencyMillis),
                source.createdAt,
                answer);
    }

    private static InterviewAnswerEntity toEntity(
            InterviewAnswer source,
            InterviewQuestionEntity question
    ) {
        InterviewAnswerEntity target = new InterviewAnswerEntity();
        target.id = source.id();
        target.question = question;
        target.content = source.content();
        target.submittedAt = source.submittedAt();
        target.createdAt = source.submittedAt();
        source.evaluation().ifPresent(evaluation -> target.evaluation = toEntity(evaluation, target));
        return target;
    }

    private static InterviewAnswer toDomain(InterviewAnswerEntity source) {
        AnswerEvaluation evaluation = source.evaluation == null ? null : toDomain(source.evaluation);
        return InterviewAnswer.reconstitute(
                source.id, source.question.id, source.content, source.submittedAt, evaluation);
    }

    private static AnswerEvaluationEntity toEntity(
            AnswerEvaluation source,
            InterviewAnswerEntity answer
    ) {
        AnswerEvaluationEntity target = new AnswerEvaluationEntity();
        target.id = source.id();
        target.answer = answer;
        target.accuracy = source.accuracy();
        target.completeness = source.completeness();
        target.depth = source.depth();
        target.clarity = source.clarity();
        target.answerOverallScore = source.answerOverallScore();
        target.strengths = new ArrayList<>(source.strengths());
        target.missingPoints = new ArrayList<>(source.missingPoints());
        target.feedback = source.feedback();
        target.evaluationVersion = source.evaluationVersion();
        copyMetadata(source.evaluationMetadata(), target);
        target.createdAt = source.createdAt();
        return target;
    }

    private static AnswerEvaluation toDomain(AnswerEvaluationEntity source) {
        return new AnswerEvaluation(
                source.id,
                source.answer.id,
                source.accuracy,
                source.completeness,
                source.depth,
                source.clarity,
                source.answerOverallScore,
                source.strengths,
                source.missingPoints,
                source.feedback,
                requiredMetadata(
                        source.provider,
                        source.modelName,
                        source.promptVersion,
                        source.tokenUsage,
                        source.latencyMillis),
                source.evaluationVersion,
                source.createdAt);
    }

    private static ReportSummary reportSummary(InterviewReportEntity source) {
        boolean anyPresent = source.strengthSummary != null
                || source.weaknessSummary != null
                || source.improvementSuggestions != null
                || source.overallComment != null;
        if (!anyPresent) {
            return null;
        }
        if (source.strengthSummary == null
                || source.weaknessSummary == null
                || source.improvementSuggestions == null
                || source.overallComment == null) {
            throw new IllegalStateException("Persisted report summary is only partially populated");
        }
        return new ReportSummary(
                source.strengthSummary,
                source.weaknessSummary,
                source.improvementSuggestions,
                source.overallComment);
    }

    private static AiCallMetadata requiredMetadata(
            String provider,
            String modelName,
            String promptVersion,
            Map<String, Long> tokenUsage,
            Long latencyMillis
    ) {
        AiCallMetadata metadata = nullableMetadata(
                provider, modelName, promptVersion, tokenUsage, latencyMillis);
        if (metadata == null) {
            throw new IllegalStateException("Required AI metadata is missing from persistence");
        }
        return metadata;
    }

    private static AiCallMetadata nullableMetadata(
            String provider,
            String modelName,
            String promptVersion,
            Map<String, Long> tokenUsage,
            Long latencyMillis
    ) {
        boolean anyPresent = provider != null
                || modelName != null
                || promptVersion != null
                || tokenUsage != null
                || latencyMillis != null;
        if (!anyPresent) {
            return null;
        }
        if (provider == null || modelName == null || promptVersion == null) {
            throw new IllegalStateException("Persisted AI metadata is only partially populated");
        }
        return new AiCallMetadata(
                provider,
                modelName,
                promptVersion,
                tokenUsage == null ? Map.of() : tokenUsage,
                latencyMillis);
    }

    private static void copyMetadata(AiCallMetadata source, InterviewQuestionEntity target) {
        target.provider = source.provider();
        target.modelName = source.modelName();
        target.promptVersion = source.promptVersion();
        target.tokenUsage = nullableTokenUsage(source.tokenUsage());
        target.latencyMillis = source.latencyMillis();
    }

    private static void copyMetadata(AiCallMetadata source, AnswerEvaluationEntity target) {
        target.provider = source.provider();
        target.modelName = source.modelName();
        target.promptVersion = source.promptVersion();
        target.tokenUsage = nullableTokenUsage(source.tokenUsage());
        target.latencyMillis = source.latencyMillis();
    }

    private static void copyMetadata(AiCallMetadata source, InterviewReportEntity target) {
        target.provider = source.provider();
        target.modelName = source.modelName();
        target.promptVersion = source.promptVersion();
        target.tokenUsage = nullableTokenUsage(source.tokenUsage());
        target.latencyMillis = source.latencyMillis();
    }

    private static Map<String, Long> nullableTokenUsage(Map<String, Long> source) {
        return source.isEmpty() ? null : Map.copyOf(source);
    }

    private static long requiredVersion(Long version, String aggregateName) {
        if (version == null) {
            throw new IllegalStateException("Persisted " + aggregateName + " has no optimistic-lock version");
        }
        return version;
    }
}
