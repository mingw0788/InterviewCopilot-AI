from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field

from interviewcopilot_ai.schemas.questions import (
    Difficulty,
    ExpectedPoint,
    QuestionType,
    Skill,
    StrictModel,
    TargetPosition,
    TokenUsage,
)


Score = Annotated[float, Field(ge=0, le=100)]


class EvaluationQuestion(StrictModel):
    question: Annotated[str, Field(min_length=1, max_length=4000)]
    topic: Annotated[str, Field(min_length=1, max_length=200)]
    difficulty: Difficulty
    expected_points: list[ExpectedPoint] = Field(min_length=1, max_length=20)
    question_type: QuestionType


class ScoringRubric(StrictModel):
    accuracy: Annotated[str, Field(min_length=1, max_length=1000)]
    completeness: Annotated[str, Field(min_length=1, max_length=1000)]
    depth: Annotated[str, Field(min_length=1, max_length=1000)]
    clarity: Annotated[str, Field(min_length=1, max_length=1000)]


class AnswerEvaluationRequest(StrictModel):
    target_position: TargetPosition
    skills: list[Skill] = Field(min_length=1, max_length=20)
    difficulty: Difficulty
    question: EvaluationQuestion
    candidate_answer: Annotated[str, Field(min_length=1, max_length=8000)]
    scoring_rubric: ScoringRubric
    language: Literal["zh-CN"]


class EvaluationMetadata(StrictModel):
    llm_provider: Annotated[str, Field(min_length=1, max_length=100)]
    model_name: Annotated[str, Field(min_length=1, max_length=200)]
    prompt_version: Literal["evaluation-v1"]
    evaluation_version: Annotated[str, Field(min_length=1, max_length=100)]
    token_usage: TokenUsage
    latency_ms: int = Field(strict=True, ge=0)


class AnswerEvaluationResponse(StrictModel):
    accuracy: Score
    completeness: Score
    depth: Score
    clarity: Score
    strengths: list[Annotated[str, Field(min_length=1, max_length=1000)]] = Field(max_length=20)
    missing_points: list[Annotated[str, Field(min_length=1, max_length=1000)]] = Field(max_length=20)
    feedback: Annotated[str, Field(min_length=1, max_length=4000)]
    metadata: EvaluationMetadata
