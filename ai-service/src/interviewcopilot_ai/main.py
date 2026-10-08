from fastapi import FastAPI

from interviewcopilot_ai.api.evaluations import create_evaluation_router
from interviewcopilot_ai.api.questions import create_question_router
from interviewcopilot_ai.api.reports import create_report_router
from interviewcopilot_ai.core.config import Settings
from interviewcopilot_ai.errors import install_error_handlers
from interviewcopilot_ai.services.question_generation import (
    MockQuestionProvider,
    QuestionGenerationService,
    QuestionProvider,
)


def create_app(
    settings: Settings | None = None,
    provider: QuestionProvider | None = None,
) -> FastAPI:
    resolved_settings = settings or Settings.from_environment()
    resolved_provider = provider or MockQuestionProvider()
    application = FastAPI(
        title="InterviewCopilot AI Service",
        version="0.1.0",
        description="Stateless AI capability service for InterviewCopilot AI.",
    )
    install_error_handlers(application)
    application.include_router(
        create_question_router(
            QuestionGenerationService(resolved_provider),
            resolved_settings.internal_service_token,
        )
    )
    application.include_router(create_evaluation_router(resolved_settings.internal_service_token))
    application.include_router(create_report_router(resolved_settings.internal_service_token))

    @application.get("/health", tags=["operations"])
    def health() -> dict[str, str]:
        return {
            "status": "UP",
            "service": "interviewcopilot-ai-service",
        }

    @application.get("/health/live", tags=["operations"])
    def liveness() -> dict[str, str]:
        return {"status": "UP"}

    @application.get("/health/ready", tags=["operations"])
    def readiness() -> dict[str, str]:
        # Readiness deliberately avoids external provider calls. Provider integration
        # belongs to a later task and must not make local health checks non-deterministic.
        return {"status": "UP"}

    return application


app = create_app()
