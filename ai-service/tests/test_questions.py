from fastapi.testclient import TestClient
import pytest

from interviewcopilot_ai.api.questions import _authenticate
from interviewcopilot_ai.core.config import Settings
from interviewcopilot_ai.errors import InternalApiError
from interviewcopilot_ai.main import create_app
from interviewcopilot_ai.services.question_generation import ProviderTimeoutError


SERVICE_TOKEN = "test-internal-service-token"
SETTINGS = Settings(provider="mock", internal_service_token=SERVICE_TOKEN)


def valid_headers() -> dict[str, str]:
    return {
        "Authorization": f"Bearer {SERVICE_TOKEN}",
        "X-Request-Id": "req_question_001",
        "X-Correlation-Id": "corr_interview_001",
        "Idempotency-Key": "question-operation-001",
    }


def valid_payload() -> dict[str, object]:
    return {
        "target_position": "Java Backend Engineer",
        "skills": ["Java", "Spring Boot", "MySQL"],
        "difficulty": "MEDIUM",
        "question_number": 2,
        "total_question_count": 5,
        "previous_questions": [
            {
                "question_number": 1,
                "question": "什么是事务隔离级别？",
                "topic": "Transaction",
                "question_type": "CONCEPTUAL",
            }
        ],
        "language": "zh-CN",
    }


def test_generate_question_returns_deterministic_structured_response() -> None:
    client = TestClient(create_app(SETTINGS))

    first = client.post(
        "/internal/v1/questions/generate",
        headers=valid_headers(),
        json=valid_payload(),
    )
    second = client.post(
        "/internal/v1/questions/generate",
        headers=valid_headers(),
        json=valid_payload(),
    )

    assert first.status_code == 200
    assert first.json() == second.json()
    assert first.headers["X-Request-Id"] == "req_question_001"
    assert first.headers["X-Correlation-Id"] == "corr_interview_001"
    assert first.json() == {
        "question": "请说明 Spring Boot 在 Java Backend Engineer 工作中的核心作用，并给出一个实际示例。",
        "topic": "Spring Boot",
        "difficulty": "MEDIUM",
        "expected_points": [
            "准确说明 Spring Boot 的核心概念",
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


def test_generate_question_rejects_invalid_service_token_without_echoing_it() -> None:
    client = TestClient(create_app(SETTINGS))
    headers = valid_headers()
    headers["Authorization"] = "Bearer leaked-invalid-token"

    response = client.post(
        "/internal/v1/questions/generate",
        headers=headers,
        json=valid_payload(),
    )

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"
    assert response.json()["request_id"] == "req_question_001"
    assert "leaked-invalid-token" not in response.text


def test_generate_question_rejects_contract_violations() -> None:
    client = TestClient(create_app(SETTINGS))
    payload = valid_payload()
    payload["question_number"] = 6
    payload["unexpected"] = "not allowed"

    response = client.post(
        "/internal/v1/questions/generate",
        headers=valid_headers(),
        json=payload,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"
    assert response.json()["request_id"] == "req_question_001"
    assert "input" not in response.json()["details"]["errors"][0]


def test_generate_question_rejects_coerced_numeric_fields() -> None:
    client = TestClient(create_app(SETTINGS))
    payload = valid_payload()
    payload["question_number"] = "2"

    response = client.post(
        "/internal/v1/questions/generate",
        headers=valid_headers(),
        json=payload,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_FAILED"


def test_generate_question_maps_malformed_json_to_bad_request() -> None:
    client = TestClient(create_app(SETTINGS))
    headers = valid_headers()
    headers["Content-Type"] = "application/json"

    response = client.post(
        "/internal/v1/questions/generate",
        headers=headers,
        content=b'{"target_position":',
    )

    assert response.status_code == 400
    assert response.json()["code"] == "INVALID_REQUEST"
    assert response.json()["request_id"] == "req_question_001"


def test_authentication_rejects_non_ascii_token_as_unauthorized() -> None:
    with pytest.raises(InternalApiError) as captured:
        _authenticate("Bearer non-ascii-令牌", SERVICE_TOKEN)

    assert captured.value.status_code == 401
    assert captured.value.code.value == "UNAUTHORIZED"


def test_generate_question_maps_invalid_provider_output_to_schema_error() -> None:
    class InvalidProvider:
        def generate(self, _request):
            return {"question": ""}

    client = TestClient(create_app(SETTINGS, InvalidProvider()))

    response = client.post(
        "/internal/v1/questions/generate",
        headers=valid_headers(),
        json=valid_payload(),
    )

    assert response.status_code == 422
    assert response.json()["code"] == "SCHEMA_VALIDATION_FAILED"
    assert response.json()["details"] == {
        "capability": "question_generation",
        "retryable": False,
    }


def test_generate_question_maps_provider_timeout() -> None:
    class TimeoutProvider:
        def generate(self, _request):
            raise ProviderTimeoutError("simulated timeout")

    client = TestClient(create_app(SETTINGS, TimeoutProvider()))

    response = client.post(
        "/internal/v1/questions/generate",
        headers=valid_headers(),
        json=valid_payload(),
    )

    assert response.status_code == 504
    assert response.json()["code"] == "LLM_TIMEOUT"
    assert response.json()["details"]["retryable"] is True


def test_generate_question_hides_unexpected_provider_error_details() -> None:
    class FailingProvider:
        def generate(self, _request):
            raise RuntimeError("provider-secret-must-not-leak")

    client = TestClient(
        create_app(SETTINGS, FailingProvider()),
        raise_server_exceptions=False,
    )

    response = client.post(
        "/internal/v1/questions/generate",
        headers=valid_headers(),
        json=valid_payload(),
    )

    assert response.status_code == 500
    assert response.json()["code"] == "INTERNAL_ERROR"
    assert response.json()["details"] == {}
    assert "provider-secret-must-not-leak" not in response.text
