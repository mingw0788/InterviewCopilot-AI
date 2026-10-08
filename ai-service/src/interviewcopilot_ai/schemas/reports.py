from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field

from interviewcopilot_ai.schemas.evaluations import Score
from interviewcopilot_ai.schemas.questions import Difficulty, Skill, StrictModel, TargetPosition, TokenUsage


class ReportMetrics(StrictModel):
    interview_overall_score: Score
    question_count: int = Field(strict=True, ge=3, le=10)
    completed_question_count: int = Field(strict=True, ge=3, le=10)
    average_accuracy: Score
    average_completeness: Score
    average_depth: Score
    average_clarity: Score
    duration_seconds: int = Field(strict=True, ge=0)


class EvaluationSummary(StrictModel):
    question_number: int = Field(strict=True, ge=1, le=10)
    answer_overall_score: Score
    strengths: list[Annotated[str, Field(min_length=1, max_length=1000)]] = Field(max_length=20)
    missing_points: list[Annotated[str, Field(min_length=1, max_length=1000)]] = Field(max_length=20)
    feedback: Annotated[str, Field(min_length=1, max_length=4000)]


class ReportGenerationRequest(StrictModel):
    target_position: TargetPosition
    skills: list[Skill] = Field(min_length=1, max_length=20)
    difficulty: Difficulty
    metrics: ReportMetrics
    evaluation_summaries: list[EvaluationSummary] = Field(min_length=3, max_length=10)
    language: Literal["zh-CN"]


class ReportMetadata(StrictModel):
    llm_provider: Annotated[str, Field(min_length=1, max_length=100)]
    model_name: Annotated[str, Field(min_length=1, max_length=200)]
    prompt_version: Literal["report-v1"]
    token_usage: TokenUsage
    latency_ms: int = Field(strict=True, ge=0)


class ReportGenerationResponse(StrictModel):
    strength_summary: Annotated[str, Field(min_length=1, max_length=4000)]
    weakness_summary: Annotated[str, Field(min_length=1, max_length=4000)]
    improvement_suggestions: list[Annotated[str, Field(min_length=1, max_length=1000)]] = Field(min_length=1, max_length=20)
    overall_comment: Annotated[str, Field(min_length=1, max_length=4000)]
    metadata: ReportMetadata
