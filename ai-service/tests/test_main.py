from fastapi.testclient import TestClient

from interviewcopilot_ai.main import app


def test_health_endpoint_reports_service_up() -> None:
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "interviewcopilot-ai-service",
    }


def test_application_metadata_is_stable() -> None:
    assert app.title == "InterviewCopilot AI Service"
    assert app.version == "0.1.0"


def test_liveness_endpoint_reports_process_up() -> None:
    response = TestClient(app).get("/health/live")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_readiness_endpoint_does_not_require_an_external_provider() -> None:
    response = TestClient(app).get("/health/ready")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}
