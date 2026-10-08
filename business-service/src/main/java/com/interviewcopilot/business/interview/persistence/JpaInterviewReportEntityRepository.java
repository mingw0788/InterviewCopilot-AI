package com.interviewcopilot.business.interview.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface JpaInterviewReportEntityRepository extends JpaRepository<InterviewReportEntity, UUID> {
    @Query("""
            select distinct report from InterviewReportEntity report
            join fetch report.session session
            left join fetch session.questions question
            left join fetch question.answer answer
            left join fetch answer.evaluation
            where session.id = :sessionId
            """)
    Optional<InterviewReportEntity> findAggregateBySessionId(@Param("sessionId") UUID sessionId);
}
