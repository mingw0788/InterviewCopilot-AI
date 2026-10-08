package com.interviewcopilot.business.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interviewcopilot.business.security.CurrentUserPrincipal;
import com.interviewcopilot.business.user.UserStatus;
import com.interviewcopilot.business.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserController {
    @GetMapping("/me")
    public UserResponse currentUser(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        return new UserResponse(
                new UserView(principal.id(), principal.loginIdentifier(), principal.status(), principal.createdAt()),
                RequestIds.resolve(servletRequest), Instant.now());
    }

    public record UserView(
            UUID id,
            @JsonProperty("login_identifier") String loginIdentifier,
            UserStatus status,
            @JsonProperty("created_at") Instant createdAt
    ) {
    }

    public record UserResponse(
            UserView data,
            @JsonProperty("request_id") String requestId,
            Instant timestamp
    ) {
    }
}
