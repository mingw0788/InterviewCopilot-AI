package com.interviewcopilot.business.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

final class AccessTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "Access token claims are invalid", null);

    private final Duration accessTokenTtl;

    AccessTokenClaimsValidator(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant issuedAt = token.getIssuedAt();
        Instant expiresAt = token.getExpiresAt();
        if (issuedAt == null
                || expiresAt == null
                || !accessTokenTtl.equals(Duration.between(issuedAt, expiresAt))
                || isBlank(token.getId())
                || !hasUuidSubject(token.getSubject())) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean hasUuidSubject(String subject) {
        if (isBlank(subject)) {
            return false;
        }
        try {
            UUID.fromString(subject);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
