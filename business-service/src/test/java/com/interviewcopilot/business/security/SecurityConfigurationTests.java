package com.interviewcopilot.business.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityConfigurationTests {
    private final BearerTokenResolver resolver = new SecurityConfiguration().bearerTokenResolver();

    @Test
    void ignoresBearerCredentialsOnPublicAuthenticationEndpoints() {
        MockHttpServletRequest login = request("POST", "/api/v1/auth/login", "Bearer malformed");
        MockHttpServletRequest registration = request("POST", "/api/v1/auth/register", "Bearer malformed");

        assertNull(resolver.resolve(login));
        assertNull(resolver.resolve(registration));
    }

    @Test
    void resolvesBearerCredentialsOnProtectedEndpoints() {
        MockHttpServletRequest request = request("GET", "/api/v1/users/me", "Bearer signed.jwt.token");

        assertEquals("signed.jwt.token", resolver.resolve(request));
    }

    private MockHttpServletRequest request(String method, String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        return request;
    }
}
