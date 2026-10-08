from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Difficulty(str, Enum):
    EASY = "EASY"
    MEDIUM = "MEDIUM"
    HARD = "HARD"


class QuestionType(str, Enum):
    CONCEPTUAL = "CONCEPTUAL"
    PRACTICAL = "PRACTICAL"
    SCENARIO = "SCENARIO"
    DESIGN = "DESIGN"


class ErrorCode(str, Enum):
    INVALID_REQUEST = "INVALID_REQUEST"
    VALIDATION_FAILED = "VALIDATION_FAILED"
    UNAUTHORIZED = "UNAUTHORIZED"
    FORBIDDEN = "FORBIDDEN"
    RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND"
    ILLEGAL_INTERVIEW_STATE = "ILLEGAL_INTERVIEW_STATE"
    INTERVIEW_NOT_READY = "INTERVIEW_NOT_READY"
    INTERVIEW_NOT_COMPLETED = "INTERVIEW_NOT_COMPLETED"
    DUPLICATE_SUBMISSION = "DUPLICATE_SUBMISSION"
    IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED"
    OPERATION_IN_PROGRESS = "OPERATION_IN_PROGRESS"
    REPORT_NOT_READY = "REPORT_NOT_READY"
    AI_TIMEOUT = "AI_TIMEOUT"
    LLM_TIMEOUT = "LLM_TIMEOUT"
    AI_SERVICE_UNAVAILABLE = "AI_SERVICE_UNAVAILABLE"
    LLM_PROVIDER_ERROR = "LLM_PROVIDER_ERROR"
    RATE_LIMITED = "RATE_LIMITED"
    INVALID_AI_RESPONSE = "INVALID_AI_RESPONSE"
    SCHEMA_VALIDATION_FAILED = "SCHEMA_VALIDATION_FAILED"
    NETWORK_ERROR = "NETWORK_ERROR"
    INTERNAL_ERROR = "INTERNAL_ERROR"


TargetPosition = Annotated[
    str,
    StringConstraints(min_length=1, max_length=100, pattern=r"^\S(?:.*\S)?$"),
]
Skill = Annotated[
    str,
    StringConstraints(min_length=1, max_length=50, pattern=r"^\S(?:.*\S)?$"),
]
QuestionText = Annotated[str, StringConstraints(min_length=1, max_length=4000)]
Topic = Annotated[str, StringConstraints(min_length=1, max_length=200)]
ExpectedPoint = Annotated[str, StringConstraints(min_length=1, max_length=1000)]


class PreviousQuestion(StrictModel):
    question_number: int = Field(strict=True, ge=1, le=10)
    question: QuestionText
    topic: Topic
    question_type: QuestionType


class QuestionGenerationRequest(StrictModel):
    target_position: TargetPosition
    skills: list[Skill] = Field(min_length=1, max_length=20)
    difficulty: Difficulty
    question_number: int = Field(strict=True, ge=1, le=10)
    total_question_count: int = Field(strict=True, ge=3, le=10)
    previous_questions: list[PreviousQuestion] = Field(max_length=9)
    language: Literal["zh-CN"]

    @model_validator(mode="after")
    def validate_cross_field_constraints(self) -> "QuestionGenerationRequest":
        if self.question_number > self.total_question_count:
            raise ValueError("question_number cannot exceed total_question_count")
        if len(self.skills) != len(set(self.skills)):
            raise ValueError("skills must contain unique values")
        return self


class TokenUsage(StrictModel):
    input_tokens: int = Field(strict=True, ge=0)
    output_tokens: int = Field(strict=True, ge=0)
    total_tokens: int = Field(strict=True, ge=0)


class QuestionMetadata(StrictModel):
    llm_provider: Annotated[str, StringConstraints(min_length=1, max_length=100)]
    model_name: Annotated[str, StringConstraints(min_length=1, max_length=200)]
    prompt_version: Literal["question-v1"]
    token_usage: TokenUsage
    latency_ms: int = Field(strict=True, ge=0)


class QuestionGenerationResponse(StrictModel):
    question: QuestionText
    topic: Topic
    difficulty: Difficulty
    expected_points: list[ExpectedPoint] = Field(min_length=1, max_length=20)
    question_type: QuestionType
    metadata: QuestionMetadata

    @model_validator(mode="after")
    def validate_expected_points_are_unique(self) -> "QuestionGenerationResponse":
        if len(self.expected_points) != len(set(self.expected_points)):
            raise ValueError("expected_points must contain unique values")
        return self


class ErrorResponse(StrictModel):
    code: ErrorCode
    message: Annotated[str, StringConstraints(min_length=1, max_length=500)]
    request_id: Annotated[str, StringConstraints(min_length=1, max_length=128)]
    timestamp: datetime
    details: dict[str, Any]
