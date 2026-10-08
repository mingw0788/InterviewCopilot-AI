package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.web.client.RestClient;

public final class AiReportClient {
    private final AiQuestionClient transport;

    AiReportClient(RestClient client, ObjectMapper mapper, Validator validator, String token) {
        this.transport = new AiQuestionClient(client, mapper, validator, token);
    }

    public ReportGenerationModels.Response generateReport(ReportGenerationModels.Request request, AiRequestContext context) {
        return transport.exchange("/internal/v1/reports/generate", request, ReportGenerationModels.Response.class, context);
    }
}
