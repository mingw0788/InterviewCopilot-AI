package com.interviewcopilot.business.integration.ai;

import com.interviewcopilot.business.web.GlobalApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiErrorHandlingTests {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void preservesSpecificTimeoutCodeFromTheInternalAiContract() throws Exception {
        mvc.perform(get("/ai-failure/timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("LLM_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("AI processing timed out"));
    }

    @Test
    void preservesSpecificProviderCodeWithoutLeakingTheInternalMessage() throws Exception {
        mvc.perform(get("/ai-failure/provider"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.message").value("AI service is temporarily unavailable"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/ai-failure/timeout")
        void timeout() {
            throw new AiClientException(
                    AiClientException.Kind.TIMEOUT,
                    "LLM_TIMEOUT",
                    "provider secret detail",
                    504,
                    "ai-request-1",
                    null);
        }

        @GetMapping("/ai-failure/provider")
        void provider() {
            throw new AiClientException(
                    AiClientException.Kind.UNAVAILABLE,
                    "LLM_PROVIDER_ERROR",
                    "provider secret detail",
                    503,
                    "ai-request-2",
                    null);
        }
    }
}
