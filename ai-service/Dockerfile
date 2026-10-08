FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    AI_SERVICE_HOST=0.0.0.0 \
    AI_SERVICE_PORT=8000

RUN addgroup --system app \
    && adduser --system --ingroup app app
WORKDIR /app
COPY pyproject.toml ./
COPY src ./src
RUN python -m pip install --no-cache-dir .

USER app
EXPOSE 8000
CMD ["sh", "-c", "exec uvicorn interviewcopilot_ai.main:app --host \"$AI_SERVICE_HOST\" --port \"$AI_SERVICE_PORT\""]
