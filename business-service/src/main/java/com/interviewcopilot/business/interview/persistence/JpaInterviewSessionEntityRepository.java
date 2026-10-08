package com.interviewcopilot.business.interview.persistence;

import com.interviewcopilot.business.interview.domain.InterviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JpaInterviewSessionEntityRepository extends JpaRepository<InterviewSessionEntity, UUID> {
    @Query("""
            select distinct session from InterviewSessionEntity session
            left join fetch session.questions question
            left join fetch question.answer answer
            left join fetch answer.evaluation
            where session.id = :id
            """)
    Optional<InterviewSessionEntity> findAggregateById(@Param("id") UUID id);

    @Query("""
            select distinct session from InterviewSessionEntity session
            left join fetch session.questions question
            left join fetch question.answer answer
            left join fetch answer.evaluation
            where session.id = :id and session.userId = :userId
            """)
    Optional<InterviewSessionEntity> findAggregateByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId
    );

    @Query(
            value = """
                    select session.id from InterviewSessionEntity session
                    where session.userId = :userId
                    order by session.createdAt desc, session.id desc
                    """,
            countQuery = """
                    select count(session) from InterviewSessionEntity session
                    where session.userId = :userId
                    """
    )
    Page<UUID> findPageIdsByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(
            value = """
                    select session.id from InterviewSessionEntity session
                    where session.userId = :userId and session.status = :status
                    order by session.createdAt desc, session.id desc
                    """,
            countQuery = """
                    select count(session) from InterviewSessionEntity session
                    where session.userId = :userId and session.status = :status
                    """
    )
    Page<UUID> findPageIdsByUserIdAndStatus(
            @Param("userId") UUID userId,
            @Param("status") InterviewStatus status,
            Pageable pageable
    );

    @Query("""
            select distinct session from InterviewSessionEntity session
            left join fetch session.questions question
            left join fetch question.answer answer
            left join fetch answer.evaluation
            where session.id in :ids
            """)
    List<InterviewSessionEntity> findAggregatesByIdIn(@Param("ids") List<UUID> ids);
}
