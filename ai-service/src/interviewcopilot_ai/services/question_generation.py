from __future__ import annotations

from typing import Any, Protocol

from pydantic import ValidationError

from interviewcopilot_ai.schemas.questions import (
    QuestionGenerationRequest,
    QuestionGenerationResponse,
)


class ProviderTimeoutError(RuntimeError):
    """Raised when a question provider exceeds its allowed execution time."""


class InvalidProviderResponseError(RuntimeError):
    """Raised when provider output does not satisfy the frozen schema."""


class QuestionProvider(Protocol):
    def generate(self, request: QuestionGenerationRequest) -> Any: ...


class MockQuestionProvider:
    """Deterministic local provider; it is enabled only through explicit configuration."""

    def generate(self, request: QuestionGenerationRequest) -> dict[str, object]:
        skill = request.skills[(request.question_number - 1) % len(request.skills)]
        return {
            "question": (
                f"请说明 {skill} 在 {request.target_position} 工作中的核心作用，"
                "并给出一个实际示例。"
            ),
            "topic": skill,
            "difficulty": request.difficulty,
            "expected_points": [
                f"准确说明 {skill} 的核心概念",
                "给出与目标岗位相关的实际示例",
            ],
            "question_type": "CONCEPTUAL",
            "metadata": {
                "llm_provider": "mock",
                "model_name": "deterministic-question-provider",
                "prompt_version": "question-v1",
                "token_usage": {
                    "input_tokens": 0,
                    "output_tokens": 0,
                    "total_tokens": 0,
                },
                "latency_ms": 0,
            },
        }


class QuestionGenerationService:
    def __init__(self, provider: QuestionProvider) -> None:
        self._provider = provider

    def generate(self, request: QuestionGenerationRequest) -> QuestionGenerationResponse:
        raw_response = self._provider.generate(request)
        try:
            return QuestionGenerationResponse.model_validate(raw_response)
        except ValidationError as error:
            raise InvalidProviderResponseError(
                "Question provider returned a response that violates the frozen schema."
            ) from error
