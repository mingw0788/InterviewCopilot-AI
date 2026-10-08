import pytest

from interviewcopilot_ai.core.config import ConfigurationError, Settings


def test_settings_load_explicit_mock_provider_and_service_token(monkeypatch) -> None:
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.setenv("INTERNAL_AI_SERVICE_TOKEN", "configured-service-token")

    settings = Settings.from_environment()

    assert settings == Settings(
        provider="mock",
        internal_service_token="configured-service-token",
    )


@pytest.mark.parametrize(
    ("provider", "service_token", "expected_setting"),
    [
        (None, "configured-service-token", "AI_PROVIDER"),
        ("openai", "configured-service-token", "AI_PROVIDER"),
        ("mock", None, "INTERNAL_AI_SERVICE_TOKEN"),
        ("mock", " surrounding-whitespace ", "INTERNAL_AI_SERVICE_TOKEN"),
        ("mock", "contains internal-space", "INTERNAL_AI_SERVICE_TOKEN"),
        ("mock", "contains\tcontrol", "INTERNAL_AI_SERVICE_TOKEN"),
        ("mock", "非-ascii-token", "INTERNAL_AI_SERVICE_TOKEN"),
    ],
)
def test_missing_or_unsafe_settings_fail_fast(
    monkeypatch,
    provider: str | None,
    service_token: str | None,
    expected_setting: str,
) -> None:
    if provider is None:
        monkeypatch.delenv("AI_PROVIDER", raising=False)
    else:
        monkeypatch.setenv("AI_PROVIDER", provider)
    if service_token is None:
        monkeypatch.delenv("INTERNAL_AI_SERVICE_TOKEN", raising=False)
    else:
        monkeypatch.setenv("INTERNAL_AI_SERVICE_TOKEN", service_token)

    with pytest.raises(ConfigurationError, match=expected_setting):
        Settings.from_environment()
