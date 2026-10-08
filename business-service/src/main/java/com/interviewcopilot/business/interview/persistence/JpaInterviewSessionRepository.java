package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.interview.domain.InterviewSession;
import com.interviewcopilot.business.interview.domain.InterviewStatus;
import com.interviewcopilot.business.interview.repository.InterviewSessionPage;
import com.interviewcopilot.business.interview.repository.InterviewSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInterviewSessionRepository implements InterviewSessionRepository {
    private static final int MAX_PAGE_SIZE = 100;

    private final JpaInterviewSessionEntityRepository entities;

    JpaInterviewSessionRepository(JpaInterviewSessionEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    @Transactional
    public InterviewSession save(InterviewSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        InterviewSessionEntity saved = entities.saveAndFlush(
                InterviewPersistenceMapper.toEntity(session, Instant.now()));
        return InterviewPersistenceMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewSession> findById(UUID id) {
        requireId(id, "id");
        return entities.findAggregateById(id).map(InterviewPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewSession> findByIdAndUserId(UUID id, UUID userId) {
        requireId(id, "id");
        requireId(userId, "userId");
        return entities.findAggregateByIdAndUserId(id, userId)
                .map(InterviewPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        requireId(id, "id");
        return entities.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewSessionPage findPageByUserId(
            UUID userId,
            Optional<InterviewStatus> status,
            int page,
            int size
    ) {
        requireId(userId, "userId");
        if (status == null) {
            throw new IllegalArgumentException("status is required; use Optional.empty() for no filter");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        PageRequest pageable = PageRequest.of(page, size);
        Page<UUID> idPage = status
                .map(value -> entities.findPageIdsByUserIdAndStatus(userId, value, pageable))
                .orElseGet(() -> entities.findPageIdsByUserId(userId, pageable));
        List<InterviewSession> content = loadInPageOrder(idPage.getContent());
        return new InterviewSessionPage(
                content,
                idPage.getNumber(),
                idPage.getSize(),
                idPage.getTotalElements(),
                idPage.getTotalPages());
    }

    private List<InterviewSession> loadInPageOrder(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, InterviewSessionEntity> entitiesById = new HashMap<>();
        for (InterviewSessionEntity entity : entities.findAggregatesByIdIn(ids)) {
            entitiesById.put(entity.id, entity);
        }
        List<InterviewSession> ordered = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            InterviewSessionEntity entity = entitiesById.get(id);
            if (entity == null) {
                throw new IllegalStateException("Interview disappeared while loading page: " + id);
            }
            ordered.add(InterviewPersistenceMapper.toDomain(entity));
        }
        return List.copyOf(ordered);
    }

    private void requireId(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
