from __future__ import annotations

from secrets import compare_digest
from typing import Annotated

from fastapi import APIRouter, Header, Response

from interviewcopilot_ai.errors import InternalApiError
from interviewcopilot_ai.schemas.questions import (
    ErrorCode,
    ErrorResponse,
    QuestionGenerationRequest,
    QuestionGenerationResponse,
)
from interviewcopilot_ai.services.question_generation import (
    InvalidProviderResponseError,
    ProviderTimeoutError,
    QuestionGenerationService,
)


def create_question_router(
    service: QuestionGenerationService,
    service_token: str,
) -> APIRouter:
    router = APIRouter(prefix="/internal/v1", tags=["Questions"])

    @router.post(
        "/questions/generate",
        response_model=QuestionGenerationResponse,
        responses={
            401: {"model": ErrorResponse},
            422: {"model": ErrorResponse},
            500: {"model": ErrorResponse},
            504: {"model": ErrorResponse},
        },
    )
    def generate_question(
        payload: QuestionGenerationRequest,
        response: Response,
        request_id: Annotated[
            str,
            Header(alias="X-Request-Id", min_length=1, max_length=128),
        ],
        correlation_id: Annotated[
            str,
            Header(alias="X-Correlation-Id", min_length=1, max_length=128),
        ],
        idempotency_key: Annotated[
            str,
            Header(
                alias="Idempotency-Key",
                min_length=8,
                max_length=128,
                pattern=r"^[!-~]+$",
            ),
        ],
        authorization: Annotated[str | None, Header(alias="Authorization")] = None,
    ) -> QuestionGenerationResponse:
        _authenticate(authorization, service_token)
        response.headers["X-Request-Id"] = request_id
        response.headers["X-Correlation-Id"] = correlation_id

        try:
            return service.generate(payload)
        except ProviderTimeoutError as error:
            raise InternalApiError(
                status_code=504,
                code=ErrorCode.LLM_TIMEOUT,
                message="The question provider timed out.",
                details={"capability": "question_generation", "retryable": True},
            ) from error
        except InvalidProviderResponseError as error:
            raise InternalApiError(
                status_code=422,
                code=ErrorCode.SCHEMA_VALIDATION_FAILED,
                message="The question provider returned an invalid structured response.",
                details={"capability": "question_generation", "retryable": False},
            ) from error

    return router


def _authenticate(authorization: str | None, expected_token: str) -> None:
    scheme, separator, supplied_token = (authorization or "").partition(" ")
    valid_scheme = separator == " " and scheme.lower() == "bearer"
    valid_token = (
        valid_scheme
        and supplied_token.isascii()
        and compare_digest(supplied_token, expected_token)
    )
    if not valid_token:
        raise InternalApiError(
            status_code=401,
            code=ErrorCode.UNAUTHORIZED,
            message="Internal service authentication is missing or invalid.",
            details={},
        )
