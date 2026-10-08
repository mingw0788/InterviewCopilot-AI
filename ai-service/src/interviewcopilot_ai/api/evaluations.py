from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Header, Response

from interviewcopilot_ai.api.questions import _authenticate
from interviewcopilot_ai.errors import InternalApiError
from interviewcopilot_ai.schemas.evaluations import AnswerEvaluationRequest, AnswerEvaluationResponse
from interviewcopilot_ai.schemas.questions import ErrorCode, ErrorResponse
from interviewcopilot_ai.services.evaluation import MockAnswerEvaluationService


def create_evaluation_router(service_token: str) -> APIRouter:
    router = APIRouter(prefix="/internal/v1", tags=["Evaluations"])
    service = MockAnswerEvaluationService()

    @router.post(
        "/evaluations/evaluate",
        response_model=AnswerEvaluationResponse,
        responses={401: {"model": ErrorResponse}, 422: {"model": ErrorResponse}},
    )
    def evaluate_answer(
        payload: AnswerEvaluationRequest,
        response: Response,
        request_id: Annotated[str, Header(alias="X-Request-Id", min_length=1, max_length=128)],
        correlation_id: Annotated[str, Header(alias="X-Correlation-Id", min_length=1, max_length=128)],
        idempotency_key: Annotated[str, Header(alias="Idempotency-Key", min_length=8, max_length=128, pattern=r"^[!-~]+$")],
        authorization: Annotated[str | None, Header(alias="Authorization")] = None,
    ) -> AnswerEvaluationResponse:
        _authenticate(authorization, service_token)
        response.headers["X-Request-Id"] = request_id
        response.headers["X-Correlation-Id"] = correlation_id
        try:
            return service.evaluate(payload)
        except ValueError as error:
            raise InternalApiError(422, ErrorCode.SCHEMA_VALIDATION_FAILED, "The evaluation provider returned an invalid structured response.", {"capability": "answer_evaluation", "retryable": False}) from error

    return router
