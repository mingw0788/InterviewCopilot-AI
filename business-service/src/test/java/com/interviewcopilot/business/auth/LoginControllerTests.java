package com.interviewcopilot.business.auth;

import com.interviewcopilot.business.web.GlobalApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginControllerTests {
    private final LoginService loginService = mock(LoginService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new LoginController(loginService))
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void successfulLoginMatchesContractAndDoesNotExposePassword() throws Exception {
        UUID userId = UUID.randomUUID();
        when(loginService.login(any())).thenReturn(new LoginService.LoginResult(
                "signed.jwt.token", 3600,
                new AuthenticatedUserView(userId, "candidate", com.interviewcopilot.business.user.UserStatus.ACTIVE)));

        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "login-request")
                        .content("""
                                {"login_identifier":"candidate","password":"secure-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.data.token_type").value("Bearer"))
                .andExpect(jsonPath("$.data.expires_in").value(3600))
                .andExpect(jsonPath("$.data.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.user.login_identifier").value("candidate"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.request_id").value("login-request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("secure-password"));
        assertFalse(response.contains("password_hash"));
    }

    @Test
    void authenticationFailureReturnsGenericUnauthorizedError() throws Exception {
        when(loginService.login(any())).thenThrow(new AuthenticationFailedException());

        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login_identifier":"unknown","password":"secure-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid login identifier or password"))
                .andExpect(jsonPath("$.details").isMap())
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("secure-password"));
    }

    @Test
    void malformedJsonAndUnknownFieldsReturnInvalidRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_identifier\":\"candidate\",\"password\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login_identifier":"candidate","password":"secure-password","extra":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void validationAndUnexpectedErrorsDoNotLeakSensitiveDetails() throws Exception {
        when(loginService.login(any())).thenThrow(new InvalidLoginRequestException("Password must be 8 to 128 characters"));
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login_identifier":"candidate","password":"short"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        reset(loginService);
        when(loginService.login(any())).thenThrow(new IllegalStateException("sensitive-internal-detail"));
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login_identifier":"candidate","password":"secure-password"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("sensitive-internal-detail"));
    }
}
