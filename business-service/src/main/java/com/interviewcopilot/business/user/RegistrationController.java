package com.interviewcopilot.business.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.interviewcopilot.business.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @RequestBody RegistrationRequest request, HttpServletRequest servletRequest) {
        RegisteredUser user = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistrationResponse(
                new RegistrationData(user), RequestIds.resolve(servletRequest), Instant.now()));
    }

    public record RegistrationData(RegisteredUser user) {
    }

    public record RegistrationResponse(
            RegistrationData data,
            @JsonProperty("request_id") String requestId,
            Instant timestamp
    ) {
    }
}
