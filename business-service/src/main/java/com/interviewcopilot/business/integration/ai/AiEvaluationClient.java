package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.web.client.RestClient;

public final class AiEvaluationClient {
    private final AiQuestionClient transport;

    AiEvaluationClient(RestClient client, ObjectMapper mapper, Validator validator, String token) {
        this.transport = new AiQuestionClient(client, mapper, validator, token);
    }

    public AnswerEvaluationModels.Response evaluate(AnswerEvaluationModels.Request request, AiRequestContext context) {
        return transport.exchange("/internal/v1/evaluations/evaluate", request, AnswerEvaluationModels.Response.class, context);
    }
}
