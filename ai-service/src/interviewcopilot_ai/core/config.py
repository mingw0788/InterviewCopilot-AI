from __future__ import annotations

import os
from dataclasses import dataclass


class ConfigurationError(RuntimeError):
    """Raised when required AI-service configuration is missing or unsafe."""


@dataclass(frozen=True, slots=True)
class Settings:
    provider: str
    internal_service_token: str

    @classmethod
    def from_environment(cls) -> "Settings":
        provider = os.getenv("AI_PROVIDER", "").strip().lower()
        if provider != "mock":
            raise ConfigurationError(
                "AI_PROVIDER must be explicitly set to 'mock' for the P0-T04 environment."
            )

        service_token = os.getenv("INTERNAL_AI_SERVICE_TOKEN", "")
        if not service_token or service_token != service_token.strip():
            raise ConfigurationError(
                "INTERNAL_AI_SERVICE_TOKEN is required and cannot contain surrounding whitespace."
            )
        if not all(0x21 <= ord(character) <= 0x7E for character in service_token):
            raise ConfigurationError(
                "INTERNAL_AI_SERVICE_TOKEN must contain visible ASCII characters only."
            )

        return cls(provider=provider, internal_service_token=service_token)
