package com.interviewcopilot.business.security;

import com.interviewcopilot.business.user.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        return new SecretKeySpec(
                properties.signingSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(signingKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey signingKey, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        DelegatingOAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer()),
                new AccessTokenClaimsValidator(properties.accessTokenTtl()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getServletPath();
            if (HttpMethod.OPTIONS.matches(request.getMethod())
                    || path.equals("/api/v1/auth/register")
                    || path.equals("/api/v1/auth/login")
                    || path.startsWith("/actuator/")) {
                return null;
            }
            return delegate.resolve(request);
        };
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            BearerTokenResolver bearerTokenResolver,
            UserRepository userRepository,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        CurrentUserFilter currentUserFilter = new CurrentUserFilter(userRepository, authenticationEntryPoint);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(currentUserFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
