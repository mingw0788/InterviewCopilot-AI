from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Header, Response

from interviewcopilot_ai.api.questions import _authenticate
from interviewcopilot_ai.schemas.questions import ErrorResponse
from interviewcopilot_ai.schemas.reports import ReportGenerationRequest, ReportGenerationResponse
from interviewcopilot_ai.services.report_generation import MockReportGenerationService


def create_report_router(service_token: str) -> APIRouter:
    router = APIRouter(prefix="/internal/v1", tags=["Reports"])
    service = MockReportGenerationService()

    @router.post(
        "/reports/generate",
        response_model=ReportGenerationResponse,
        responses={401: {"model": ErrorResponse}, 422: {"model": ErrorResponse}},
    )
    def generate_report(
        payload: ReportGenerationRequest,
        response: Response,
        request_id: Annotated[str, Header(alias="X-Request-Id", min_length=1, max_length=128)],
        correlation_id: Annotated[str, Header(alias="X-Correlation-Id", min_length=1, max_length=128)],
        idempotency_key: Annotated[str, Header(alias="Idempotency-Key", min_length=8, max_length=128, pattern=r"^[!-~]+$")],
        authorization: Annotated[str | None, Header(alias="Authorization")] = None,
    ) -> ReportGenerationResponse:
        _authenticate(authorization, service_token)
        response.headers["X-Request-Id"] = request_id
        response.headers["X-Correlation-Id"] = correlation_id
        return service.generate(payload)

    return router
