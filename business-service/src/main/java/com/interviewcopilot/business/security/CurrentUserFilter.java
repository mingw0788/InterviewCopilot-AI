package com.interviewcopilot.business.security;

import com.interviewcopilot.business.user.UserAccount;
import com.interviewcopilot.business.user.UserRepository;
import com.interviewcopilot.business.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class CurrentUserFilter extends OncePerRequestFilter {
    private final UserRepository userRepository;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    public CurrentUserFilter(UserRepository userRepository, ApiAuthenticationEntryPoint authenticationEntryPoint) {
        this.userRepository = userRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/v1/")
                || request.getServletPath().equals("/api/v1/auth/register")
                || request.getServletPath().equals("/api/v1/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwtAuthentication.getToken().getSubject());
        } catch (IllegalArgumentException exception) {
            reject(request, response);
            return;
        }

        UserAccount account;
        try {
            account = userRepository.findById(userId).orElse(null);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            throw exception;
        }
        if (account == null || account.status() != UserStatus.ACTIVE) {
            reject(request, response);
            return;
        }

        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                account.id(), account.loginIdentifier(), account.status(), account.createdAt());
        UsernamePasswordAuthenticationToken currentUserAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, jwtAuthentication.getAuthorities());
        currentUserAuthentication.setDetails(jwtAuthentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(currentUserAuthentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request, response, new BadCredentialsException("Invalid access token subject or user status"));
    }
}
