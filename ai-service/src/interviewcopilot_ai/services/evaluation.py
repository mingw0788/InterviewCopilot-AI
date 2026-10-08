from __future__ import annotations

from interviewcopilot_ai.schemas.evaluations import (
    AnswerEvaluationRequest,
    AnswerEvaluationResponse,
    EvaluationMetadata,
)
from interviewcopilot_ai.schemas.questions import TokenUsage


class MockAnswerEvaluationService:
    """Deterministic local evaluation used when AI_PROVIDER=mock."""

    def evaluate(self, request: AnswerEvaluationRequest) -> AnswerEvaluationResponse:
        answer_length = len(request.candidate_answer.strip())
        score = 80.0 if answer_length >= 20 else 70.0
        return AnswerEvaluationResponse(
            accuracy=score,
            completeness=score,
            depth=score - 2.0,
            clarity=score + 2.0,
            strengths=[
                f"围绕 {request.question.topic} 给出了明确回答",
                "回答包含可验证的实现或设计信息",
            ],
            missing_points=["可进一步补充边界条件和异常场景"],
            feedback="回答结构清晰，建议继续补充边界条件、失败恢复和可观测性分析。",
            metadata=EvaluationMetadata(
                llm_provider="mock",
                model_name="deterministic-evaluation-provider",
                prompt_version="evaluation-v1",
                evaluation_version="rubric-v1",
                token_usage=TokenUsage(input_tokens=0, output_tokens=0, total_tokens=0),
                latency_ms=0,
            ),
        )
