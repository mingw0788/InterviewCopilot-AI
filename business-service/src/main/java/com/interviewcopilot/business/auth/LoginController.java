package com.interviewcopilot.business.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interviewcopilot.business.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {
    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        LoginService.LoginResult result = loginService.login(request);
        return ResponseEntity.ok(new LoginResponse(
                new LoginData(result.accessToken(), "Bearer", result.expiresIn(), result.user()),
                RequestIds.resolve(servletRequest), Instant.now()));
    }

    public record LoginData(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            AuthenticatedUserView user
    ) {
    }

    public record LoginResponse(
            LoginData data,
            @JsonProperty("request_id") String requestId,
            Instant timestamp
    ) {
    }
}
