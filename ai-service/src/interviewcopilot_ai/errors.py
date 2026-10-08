from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from interviewcopilot_ai.schemas.questions import ErrorCode, ErrorResponse


@dataclass(slots=True)
class InternalApiError(Exception):
    status_code: int
    code: ErrorCode
    message: str
    details: dict[str, Any] = field(default_factory=dict)


def request_id_for(request: Request) -> str:
    supplied = request.headers.get("X-Request-Id", "")
    if 1 <= len(supplied) <= 128:
        return supplied
    return f"req_{uuid4().hex}"


def error_response(
    request: Request,
    *,
    status_code: int,
    code: ErrorCode,
    message: str,
    details: dict[str, Any],
) -> JSONResponse:
    payload = ErrorResponse(
        code=code,
        message=message,
        request_id=request_id_for(request),
        timestamp=datetime.now(timezone.utc),
        details=details,
    )
    return JSONResponse(status_code=status_code, content=payload.model_dump(mode="json"))


def install_error_handlers(application: FastAPI) -> None:
    @application.exception_handler(InternalApiError)
    async def handle_internal_api_error(
        request: Request,
        error: InternalApiError,
    ) -> JSONResponse:
        return error_response(
            request,
            status_code=error.status_code,
            code=error.code,
            message=error.message,
            details=error.details,
        )

    @application.exception_handler(RequestValidationError)
    async def handle_validation_error(
        request: Request,
        error: RequestValidationError,
    ) -> JSONResponse:
        malformed_json = any(item["type"] == "json_invalid" for item in error.errors())
        safe_errors = [
            {
                "location": [str(part) for part in item["loc"]],
                "message": item["msg"],
                "type": item["type"],
            }
            for item in error.errors()
        ]
        return error_response(
            request,
            status_code=400 if malformed_json else 422,
            code=ErrorCode.INVALID_REQUEST if malformed_json else ErrorCode.VALIDATION_FAILED,
            message=(
                "The internal AI request contains malformed JSON."
                if malformed_json
                else "The internal AI request failed validation."
            ),
            details={"errors": safe_errors},
        )

    @application.exception_handler(Exception)
    async def handle_unexpected_error(request: Request, _: Exception) -> JSONResponse:
        return error_response(
            request,
            status_code=500,
            code=ErrorCode.INTERNAL_ERROR,
            message="The AI service encountered an unexpected error.",
            details={},
        )
