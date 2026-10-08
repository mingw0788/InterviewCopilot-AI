from fastapi.testclient import TestClient

from interviewcopilot_ai.core.config import Settings
from interviewcopilot_ai.main import create_app


TOKEN = "test-internal-service-token"
HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "X-Request-Id": "req_capability_001",
    "X-Correlation-Id": "corr_capability_001",
    "Idempotency-Key": "capability-operation-001",
}


def client() -> TestClient:
    return TestClient(create_app(Settings(provider="mock", internal_service_token=TOKEN)))


def evaluation_payload() -> dict[str, object]:
    return {
        "target_position": "Java Backend Engineer",
        "skills": ["Java", "Spring Boot", "MySQL"],
        "difficulty": "MEDIUM",
        "question": {
            "question": "请解释事务传播行为。",
            "topic": "Spring Transaction",
            "difficulty": "MEDIUM",
            "expected_points": ["解释 REQUIRED", "说明事务边界"],
            "question_type": "CONCEPTUAL",
        },
        "candidate_answer": "REQUIRED 会加入已有事务，并且需要说明异常边界。",
        "scoring_rubric": {
            "accuracy": "technical correctness",
            "completeness": "coverage",
            "depth": "boundary analysis",
            "clarity": "readability",
        },
        "language": "zh-CN",
    }


def report_payload() -> dict[str, object]:
    return {
        "target_position": "Java Backend Engineer",
        "skills": ["Java", "Spring Boot", "MySQL"],
        "difficulty": "MEDIUM",
        "metrics": {
            "interview_overall_score": 80,
            "question_count": 3,
            "completed_question_count": 3,
            "average_accuracy": 80,
            "average_completeness": 80,
            "average_depth": 78,
            "average_clarity": 82,
            "duration_seconds": 120,
        },
        "evaluation_summaries": [
            {
                "question_number": index,
                "answer_overall_score": 80,
                "strengths": ["回答清晰"],
                "missing_points": ["补充边界"],
                "feedback": "继续补充实际场景。",
            }
            for index in range(1, 4)
        ],
        "language": "zh-CN",
    }


def test_evaluation_endpoint_returns_contract_valid_mock_response() -> None:
    response = client().post("/internal/v1/evaluations/evaluate", headers=HEADERS, json=evaluation_payload())

    assert response.status_code == 200
    assert response.json()["metadata"]["prompt_version"] == "evaluation-v1"
    assert response.json()["accuracy"] == 80.0
    assert response.headers["X-Request-Id"] == "req_capability_001"


def test_report_endpoint_returns_contract_valid_mock_response() -> None:
    response = client().post("/internal/v1/reports/generate", headers=HEADERS, json=report_payload())

    assert response.status_code == 200
    assert response.json()["metadata"]["prompt_version"] == "report-v1"
    assert response.json()["improvement_suggestions"]


def test_capability_endpoints_require_service_authentication() -> None:
    headers = dict(HEADERS)
    headers["Authorization"] = "Bearer wrong-token"

    response = client().post("/internal/v1/evaluations/evaluate", headers=headers, json=evaluation_payload())

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"
