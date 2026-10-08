package com.interviewcopilot.business.user;

import com.interviewcopilot.business.web.GlobalApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationControllerTests {
    private final RegistrationService service = mock(RegistrationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RegistrationController(service))
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void registrationReturnsContractResponseWithoutPassword() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.register(any())).thenReturn(new RegisteredUser(
                id, "candidate", UserStatus.ACTIVE, Instant.parse("2026-10-07T00:00:00Z")));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "request-123")
                        .content("""
                                {"login_identifier":"candidate","password":"secure-password"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id").value(id.toString()))
                .andExpect(jsonPath("$.data.user.login_identifier").value("candidate"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.created_at").exists())
                .andExpect(jsonPath("$.request_id").value("request-123"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.user.password_hash").doesNotExist());
    }

    @Test
    void duplicateAccountGetsStableConflictError() throws Exception {
        when(service.register(any())).thenThrow(new DuplicateRegistrationException());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login_identifier":"candidate","password":"secure-password"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SUBMISSION"))
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void invalidFieldsGetValidationError() throws Exception {
        when(service.register(any())).thenThrow(new InvalidRegistrationException("Password must be 8 to 128 characters"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login_identifier":"candidate","password":"short"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownFieldsGetInvalidRequestWithoutEchoingSecrets() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_identifier\":\"candidate\",\"password\":\"secret\",\"extra\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    @Test
    void malformedJsonGetsInvalidRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_identifier\":\"candidate\",\"password\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unexpectedFailuresDoNotLeakExceptionMessages() throws Exception {
        when(service.register(any())).thenThrow(new IllegalStateException("secret-password-internal"));

        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login_identifier":"candidate","password":"secure-password"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("secret-password-internal"));
        assertFalse(response.contains("secure-password"));
    }

    @Test
    void unsupportedContentTypeGetsContractError() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("login_identifier=candidate&password=secure-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
