import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import openapiTS, { astToString } from "openapi-typescript";
import { parse } from "yaml";

const businessUrl = new URL("../business-api.yaml", import.meta.url);
const internalUrl = new URL("../ai-internal-api.yaml", import.meta.url);

const [businessSource, internalSource] = await Promise.all([
  readFile(businessUrl, "utf8"),
  readFile(internalUrl, "utf8"),
]);

const business = parse(businessSource);
const internal = parse(internalSource);

const httpMethods = new Set([
  "get",
  "put",
  "post",
  "delete",
  "options",
  "head",
  "patch",
  "trace",
]);

const frozenErrorCodes = [
  "INVALID_REQUEST",
  "VALIDATION_FAILED",
  "UNAUTHORIZED",
  "FORBIDDEN",
  "RESOURCE_NOT_FOUND",
  "ILLEGAL_INTERVIEW_STATE",
  "INTERVIEW_NOT_READY",
  "INTERVIEW_NOT_COMPLETED",
  "DUPLICATE_SUBMISSION",
  "IDEMPOTENCY_KEY_REUSED",
  "OPERATION_IN_PROGRESS",
  "REPORT_NOT_READY",
  "AI_TIMEOUT",
  "LLM_TIMEOUT",
  "AI_SERVICE_UNAVAILABLE",
  "LLM_PROVIDER_ERROR",
  "RATE_LIMITED",
  "INVALID_AI_RESPONSE",
  "SCHEMA_VALIDATION_FAILED",
  "NETWORK_ERROR",
  "INTERNAL_ERROR",
];

function operations(document) {
  return Object.entries(document.paths).flatMap(([path, pathItem]) =>
    Object.entries(pathItem)
      .filter(([method]) => httpMethods.has(method))
      .map(([method, operation]) => ({ path, method, operation, pathItem })),
  );
}

function resolvePointer(document, reference) {
  assert.match(reference, /^#\//, `Only local references are allowed: ${reference}`);
  return reference
    .slice(2)
    .split("/")
    .map((part) => part.replaceAll("~1", "/").replaceAll("~0", "~"))
    .reduce((value, part) => value[part], document);
}

function dereference(value, document, resolving = new Set()) {
  if (Array.isArray(value)) {
    return value.map((item) => dereference(item, document, resolving));
  }
  if (value === null || typeof value !== "object") {
    return value;
  }
  if (typeof value.$ref === "string") {
    assert(!resolving.has(value.$ref), `Circular reference: ${value.$ref}`);
    const nextResolving = new Set(resolving).add(value.$ref);
    const target = dereference(resolvePointer(document, value.$ref), document, nextResolving);
    const siblings = Object.fromEntries(
      Object.entries(value)
        .filter(([key]) => key !== "$ref")
        .map(([key, item]) => [key, dereference(item, document, resolving)]),
    );
    return { ...target, ...siblings };
  }
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [
      key,
      dereference(item, document, resolving),
    ]),
  );
}

function schemaValidator(document, schemaName) {
  const ajv = new Ajv2020({
    allErrors: true,
    strict: false,
    multipleOfPrecision: 4,
  });
  addFormats(ajv);
  return ajv.compile(dereference(document.components.schemas[schemaName], document));
}

function assertValid(validate, value, context) {
  assert.equal(validate(value), true, `${context}: ${JSON.stringify(validate.errors)}`);
}

function collectPropertyNames(value, names = new Set()) {
  if (Array.isArray(value)) {
    value.forEach((item) => collectPropertyNames(item, names));
    return names;
  }
  if (value === null || typeof value !== "object") {
    return names;
  }
  if (value.properties) {
    Object.keys(value.properties).forEach((name) => names.add(name));
  }
  Object.values(value).forEach((item) => collectPropertyNames(item, names));
  return names;
}

function successSchemas(document) {
  return operations(document).flatMap(({ operation }) =>
    Object.entries(operation.responses)
      .filter(([status]) => /^2\d\d$/.test(status))
      .map(([, response]) => {
        const resolvedResponse = dereference(response, document);
        return dereference(
          resolvedResponse.content["application/json"].schema,
          document,
        );
      }),
  );
}

test("documents expose only the frozen endpoint catalogs", () => {
  const businessCatalog = operations(business).map(({ method, path }) => `${method} ${path}`);
  assert.deepEqual(businessCatalog, [
    "post /auth/register",
    "post /auth/login",
    "get /users/me",
    "post /interviews",
    "get /interviews",
    "get /interviews/{interviewId}",
    "post /interviews/{interviewId}/start",
    "get /interviews/{interviewId}/current-question",
    "post /interviews/{interviewId}/answers",
    "post /interviews/{interviewId}/cancel",
    "get /interviews/{interviewId}/report",
    "post /interviews/{interviewId}/report/retry",
  ]);

  const internalCatalog = operations(internal).map(({ method, path }) => `${method} ${path}`);
  assert.deepEqual(internalCatalog, [
    "post /questions/generate",
    "post /evaluations/evaluate",
    "post /reports/generate",
  ]);
});

test("operation IDs are present and unique", () => {
  for (const document of [business, internal]) {
    const ids = operations(document).map(({ operation }) => operation.operationId);
    assert(ids.every(Boolean));
    assert.equal(new Set(ids).size, ids.length);
  }
});

test("business authentication and idempotency boundaries are explicit", () => {
  assert.deepEqual(business.paths["/auth/register"].post.security, []);
  assert.deepEqual(business.paths["/auth/login"].post.security, []);
  assert.deepEqual(business.security, [{ bearerAuth: [] }]);

  const commands = [
    ["/interviews", "post"],
    ["/interviews/{interviewId}/start", "post"],
    ["/interviews/{interviewId}/answers", "post"],
    ["/interviews/{interviewId}/cancel", "post"],
    ["/interviews/{interviewId}/report/retry", "post"],
  ];
  for (const [path, method] of commands) {
    const pathItem = business.paths[path];
    const parameters = [...(pathItem.parameters ?? []), ...(pathItem[method].parameters ?? [])]
      .map((parameter) => dereference(parameter, business));
    const idempotencyHeader = parameters.find(({ name }) => name === "Idempotency-Key");
    assert(
      idempotencyHeader,
      `${method} ${path} must require Idempotency-Key`,
    );
    assert.equal(idempotencyHeader.required, true);
  }
});

test("every internal operation requires service auth and tracing headers", () => {
  assert.deepEqual(internal.security, [{ serviceBearerAuth: [] }]);
  const requiredHeaders = ["X-Request-Id", "X-Correlation-Id", "Idempotency-Key"];

  for (const { path, method, operation, pathItem } of operations(internal)) {
    const parameters = [...(pathItem.parameters ?? []), ...(operation.parameters ?? [])]
      .map((parameter) => dereference(parameter, internal));
    assert.deepEqual(parameters.map(({ name }) => name), requiredHeaders);
    assert(
      parameters.every(({ required }) => required === true),
      `${method} ${path} must require all internal headers`,
    );
  }
});

test("error codes and error shape match the architecture freeze", () => {
  assert.deepEqual(business.components.schemas.ErrorCode.enum, frozenErrorCodes);
  assert.deepEqual(internal.components.schemas.ErrorCode.enum, frozenErrorCodes);

  for (const document of [business, internal]) {
    assert.deepEqual(document.components.schemas.ErrorResponse.required, [
      "code",
      "message",
      "request_id",
      "timestamp",
      "details",
    ]);
  }
});

test("business success responses use the frozen envelope", () => {
  for (const schema of successSchemas(business)) {
    assert.deepEqual(schema.required, ["data", "request_id", "timestamp"]);
  }
});

test("public responses do not expose internal AI data", () => {
  const names = successSchemas(business).reduce(
    (collected, schema) => collectPropertyNames(schema, collected),
    new Set(),
  );
  for (const forbidden of [
    "expected_points",
    "prompt_version",
    "model_name",
    "provider",
    "llm_provider",
    "token_usage",
  ]) {
    assert(!names.has(forbidden), `Public contract exposes ${forbidden}`);
  }
});

test("AI responses cannot decide business state or final scores", () => {
  const names = successSchemas(internal).reduce(
    (collected, schema) => collectPropertyNames(schema, collected),
    new Set(),
  );
  for (const forbidden of [
    "session_status",
    "interview_status",
    "answer_status",
    "can_continue",
    "can_complete",
    "overall_score",
    "answer_overall_score",
    "interview_overall_score",
  ]) {
    assert(!names.has(forbidden), `AI response controls business field ${forbidden}`);
  }
});

test("internal requests contain no credential or user-identity payload", () => {
  const requestSchemas = operations(internal).map(({ operation }) =>
    dereference(operation.requestBody.content["application/json"].schema, internal),
  );
  const names = requestSchemas.reduce(
    (collected, schema) => collectPropertyNames(schema, collected),
    new Set(),
  );
  for (const forbidden of ["user_id", "password", "access_token", "jwt"] ) {
    assert(!names.has(forbidden), `Internal payload exposes ${forbidden}`);
  }
});

test("frozen enums, ranges, and deterministic score ownership are encoded", () => {
  assert.deepEqual(business.components.schemas.InterviewStatus.enum, [
    "CREATED",
    "IN_PROGRESS",
    "COMPLETED",
    "CANCELLED",
    "FAILED",
  ]);
  const count = business.components.schemas.CreateInterviewRequest.properties.question_count;
  assert.equal(count.minimum, 3);
  assert.equal(count.maximum, 10);
  assert.equal(count.default, 5);

  const score = business.components.schemas.Score;
  assert.equal(score.minimum, 0);
  assert.equal(score.maximum, 100);
  assert.equal(score.multipleOf, 0.01);

  const scoreDescription =
    business.components.schemas.PublicEvaluation.properties.answer_overall_score.description;
  assert.match(scoreDescription, /Java/);
  assert.match(scoreDescription, /40\/25\/20\/15/);
  assert.match(scoreDescription, /HALF_UP/);
});

test("completed reports expose deterministic metrics while AI fields remain nullable", () => {
  const reportOperation = business.paths["/interviews/{interviewId}/report"].get;
  assert(reportOperation.responses["200"]);
  assert(reportOperation.responses["409"]);
  assert.match(reportOperation.description, /always returns HTTP 200/);

  const pendingSchema = business.components.schemas.PendingReport;
  const availableSchema = business.components.schemas.AvailableReport;
  const metricFields = [
    "interview_overall_score",
    "average_accuracy",
    "average_completeness",
    "average_depth",
    "average_clarity",
  ];
  assert(metricFields.every((field) => pendingSchema.required.includes(field)));
  assert(metricFields.every((field) => availableSchema.required.includes(field)));

  const narrativeFields = [
    "strength_summary",
    "weakness_summary",
    "improvement_suggestions",
    "overall_comment",
    "published_at",
  ];
  assert(narrativeFields.every((field) => pendingSchema.properties[field].type === "null"));
  assert(narrativeFields.every((field) => availableSchema.properties[field].type !== "null"));

  const validate = schemaValidator(business, "Report");
  const available = structuredClone(business.components.schemas.ReportResponse.example.data);
  assertValid(validate, available, "available report");
  const pending = {
    ...available,
    report_status: "PENDING",
    strength_summary: null,
    weakness_summary: null,
    improvement_suggestions: null,
    overall_comment: null,
    published_at: null,
  };
  assertValid(validate, pending, "pending report");
  assert.equal(validate({ ...pending, strength_summary: "premature summary" }), false);
  assert.equal(validate({ ...available, overall_comment: null }), false);
});

test("state-dependent business responses reject contradictory combinations", () => {
  const currentQuestion = schemaValidator(business, "CurrentQuestionData");
  const activeQuestion = structuredClone(
    business.components.schemas.CurrentQuestionResponse.example.data,
  );
  assertValid(currentQuestion, activeQuestion, "active current question");

  const completedQuestion = {
    interview_id: activeQuestion.interview_id,
    interview_status: "COMPLETED",
    question_number: null,
    total_question_count: 5,
    question: null,
    answer_status: null,
  };
  assertValid(currentQuestion, completedQuestion, "completed current question");
  assert.equal(
    currentQuestion({ ...completedQuestion, question: activeQuestion.question }),
    false,
  );
  assert.equal(currentQuestion({ ...activeQuestion, question: null }), false);

  const answerResult = schemaValidator(business, "SubmitAnswerData");
  const inProgress = structuredClone(
    business.components.schemas.SubmitAnswerResponse.example.data,
  );
  assertValid(answerResult, inProgress, "in-progress answer result");

  const completed = {
    answer: inProgress.answer,
    evaluation: inProgress.evaluation,
    next_question: null,
    interview_status: "COMPLETED",
    report_status: "PENDING",
  };
  assertValid(answerResult, completed, "completed answer result");
  assert.equal(answerResult({ ...completed, next_question: inProgress.next_question }), false);
  const { report_status: _reportStatus, ...withoutReportStatus } = completed;
  assert.equal(answerResult(withoutReportStatus), false);
  assert.equal(answerResult({ ...inProgress, report_status: "PENDING" }), false);
});

test("all component examples satisfy their schemas", () => {
  for (const [label, document] of [["business", business], ["internal", internal]]) {
    let exampleCount = 0;
    for (const [name, schema] of Object.entries(document.components.schemas)) {
      if (!Object.hasOwn(schema, "example")) {
        continue;
      }
      exampleCount += 1;
      assertValid(schemaValidator(document, name), schema.example, `${label}.${name}`);
    }
    assert(exampleCount >= 5, `${label} contract needs representative examples`);
  }
});

test("request and AI-output schemas reject invalid boundary values", () => {
  const register = schemaValidator(business, "RegisterRequest");
  assert.equal(register({ login_identifier: "ab", password: "secure-password" }), false);
  assert.equal(register({ login_identifier: " candidate ", password: "secure-password" }), false);
  assert.equal(register({ login_identifier: "candidate", password: "short" }), false);

  const create = schemaValidator(business, "CreateInterviewRequest");
  const validInterview = {
    target_position: "Java Engineer",
    skills: ["Java"],
    difficulty: "MEDIUM",
  };
  assertValid(create, validInterview, "default question count request");
  assert.equal(create({ ...validInterview, question_count: 2 }), false);
  assert.equal(create({ ...validInterview, question_count: 11 }), false);

  const answer = schemaValidator(business, "SubmitAnswerRequest");
  const questionId = "4c6f223f-2823-43ed-a5c4-7692c650890d";
  assert.equal(answer({ question_id: questionId, answer_content: "   " }), false);
  assert.equal(answer({ question_id: questionId, answer_content: "x".repeat(8001) }), false);

  const questionRequest = schemaValidator(internal, "QuestionGenerationRequest");
  const requestExample = structuredClone(
    internal.components.schemas.QuestionGenerationRequest.example,
  );
  assertValid(questionRequest, requestExample, "question generation request");
  assert.equal(questionRequest({ ...requestExample, language: "en-US" }), false);

  const evaluationResponse = schemaValidator(internal, "AnswerEvaluationResponse");
  const responseExample = structuredClone(
    internal.components.schemas.AnswerEvaluationResponse.example,
  );
  assert.equal(evaluationResponse({ ...responseExample, accuracy: 100.01 }), false);
  assert.equal(evaluationResponse({ ...responseExample, answer_overall_score: 82.55 }), false);
});

test("both contracts support TypeScript generation without emitting files", async () => {
  for (const [label, url] of [["business", businessUrl], ["internal", internalUrl]]) {
    const generated = astToString(await openapiTS(url, { silent: true }));
    assert.match(generated, /export interface paths/);
    assert(generated.length > 1000, `${label} generated output is unexpectedly empty`);
  }
});
