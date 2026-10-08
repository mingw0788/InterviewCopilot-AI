package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.interview.domain.InterviewReport;
import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.repository.InterviewReportRepository;
import com.interviewcopilot.business.interview.repository.InterviewSessionRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInterviewReportRepository implements InterviewReportRepository {
    private final JpaInterviewReportEntityRepository entities;
    private final JpaInterviewSessionEntityRepository sessionEntities;
    private final InterviewSessionRepository sessions;

    JpaInterviewReportRepository(
            JpaInterviewReportEntityRepository entities,
            JpaInterviewSessionEntityRepository sessionEntities,
            InterviewSessionRepository sessions
    ) {
        this.entities = entities;
        this.sessionEntities = sessionEntities;
        this.sessions = sessions;
    }

    @Override
    @Transactional
    public InterviewReport save(InterviewReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        InterviewSession completedSession = requireCompletedSession(report.sessionId());
        InterviewSessionEntity sessionReference = sessionEntities.getReferenceById(report.sessionId());
        Optional<InterviewReportEntity> existing = entities.findById(report.id());
        InterviewReportEntity saved;
        if (existing.isPresent()) {
            saved = existing.get();
            if (report.persistenceVersion().isEmpty()
                    || report.persistenceVersion().getAsLong() != saved.version) {
                throw new ObjectOptimisticLockingFailureException(InterviewReportEntity.class, report.id());
            }
            InterviewPersistenceMapper.copyReportFields(report, saved, sessionReference, Instant.now());
            entities.flush();
        } else {
            if (report.persistenceVersion().isPresent()) {
                throw new ObjectOptimisticLockingFailureException(InterviewReportEntity.class, report.id());
            }
            saved = entities.saveAndFlush(
                    InterviewPersistenceMapper.toEntity(report, sessionReference, Instant.now()));
        }
        return InterviewPersistenceMapper.toDomain(saved, completedSession);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewReport> findBySessionId(UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }
        return entities.findAggregateBySessionId(sessionId)
                .map(entity -> InterviewPersistenceMapper.toDomain(
                        entity, InterviewPersistenceMapper.toDomain(entity.session)));
    }

    private InterviewSession requireCompletedSession(UUID sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Report references a missing interview session: " + sessionId));
    }
}
