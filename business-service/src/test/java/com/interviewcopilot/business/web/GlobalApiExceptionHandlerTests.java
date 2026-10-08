package com.interviewcopilot.business.web;

import com.interviewcopilot.business.interview.domain.DomainRuleViolationException;
import com.interviewcopilot.business.interview.domain.DomainValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalApiExceptionHandlerTests {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void preservesFrozenApiErrorsAndSafeDetails() throws Exception {
        mvc.perform(get("/failure/api").header("X-Request-Id", "request-006"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.request_id").value("request-006"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details.interview_id").value("interview-1"));
    }

    @Test
    void mapsDomainValidationAndStateFailures() throws Exception {
        mvc.perform(get("/failure/validation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get("/failure/state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_INTERVIEW_STATE"));
    }

    @Test
    void mapsMalformedBodiesToInvalidRequest() throws Exception {
        mvc.perform(post("/failure/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void mapsMissingResourcesToTheFrozenNotFoundShape() throws Exception {
        mvc.perform(get("/failure/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    void unexpectedFailuresNeverLeakInternalMessages() throws Exception {
        String response = mvc.perform(get("/failure/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("database-password"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/failure/api")
        void api() {
            throw new ApiException(
                    ApiErrorCode.FORBIDDEN, "Access denied", Map.of("interview_id", "interview-1"));
        }

        @GetMapping("/failure/validation")
        void validation() {
            throw new DomainValidationException("Invalid interview configuration");
        }

        @GetMapping("/failure/state")
        void state() {
            throw new DomainRuleViolationException("Interview is completed");
        }

        @PostMapping("/failure/body")
        void body(@RequestBody Map<String, Object> body) {
        }

        @GetMapping("/failure/unexpected")
        void unexpected() {
            throw new IllegalStateException("database-password");
        }

        @GetMapping("/failure/not-found")
        void notFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing");
        }
    }
}
