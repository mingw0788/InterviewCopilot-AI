# V1 OpenAPI contracts

These documents are the Task P0-T02 contract baseline:

- `business-api.yaml` defines the browser-facing Java API under `/api/v1`.
- `ai-internal-api.yaml` defines the service-only Python API under `/internal/v1`.

The final Technical Design Specification and Architecture Freeze take precedence over earlier provisional examples. In particular, V1 uses a username-style login identifier of 3–50 characters, 3–10 questions with a default of 5, a 60-minute access JWT, and HTTP 200 report reads after interview completion even while AI narrative fields are not yet available.

No Controller, Router, generated client, or service implementation is part of this task. The generation smoke test works in memory and does not create generated source files.

## Validate

Use a project-supported Node.js version and run:

```text
npm ci
npm test
```

The test command performs standards linting, schema/example validation, architecture-boundary checks, negative validation tests, and a TypeScript generation smoke test for both contracts.
