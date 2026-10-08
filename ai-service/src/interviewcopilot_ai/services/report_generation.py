from __future__ import annotations

from interviewcopilot_ai.schemas.questions import TokenUsage
from interviewcopilot_ai.schemas.reports import (
    ReportGenerationRequest,
    ReportGenerationResponse,
    ReportMetadata,
)


class MockReportGenerationService:
    """Deterministic local report narrative used when AI_PROVIDER=mock."""

    def generate(self, request: ReportGenerationRequest) -> ReportGenerationResponse:
        score = request.metrics.interview_overall_score
        return ReportGenerationResponse(
            strength_summary=(
                f"候选人在 {request.target_position} 面试中完成了 "
                f"{request.metrics.completed_question_count} 道题，整体得分为 {score}。"
            ),
            weakness_summary="复杂边界条件、异常处理和可观测性分析仍有提升空间。",
            improvement_suggestions=[
                "练习复杂事务、并发和失败恢复场景",
                "使用固定结构补充监控指标和验证方案",
            ],
            overall_comment="整体具备继续深入后端开发的基础，建议结合实际项目进行专项训练。",
            metadata=ReportMetadata(
                llm_provider="mock",
                model_name="deterministic-report-provider",
                prompt_version="report-v1",
                token_usage=TokenUsage(input_tokens=0, output_tokens=0, total_tokens=0),
                latency_ms=0,
            ),
        )
