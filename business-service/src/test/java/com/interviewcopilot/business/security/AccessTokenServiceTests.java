package com.interviewcopilot.business.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessTokenServiceTests {
    private static final String SECRET = "unit-test-jwt-signing-key-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("72a779e2-f8cf-47f0-9e7a-23b9c8a2a02f");

    private JwtProperties properties;
    private SecurityConfiguration configuration;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(
                "https://interviewcopilot.local/business-service", SECRET, Duration.ofMinutes(60));
        configuration = new SecurityConfiguration();
        signingKey = configuration.jwtSigningKey(properties);
    }

    @Test
    void issuesHs256TokenWithFrozenLifetimeAndRequiredClaims() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AccessTokenService service = new AccessTokenService(
                configuration.jwtEncoder(signingKey), properties, clock);

        AccessTokenService.IssuedAccessToken issued = service.issue(USER_ID);
        JwtDecoder decoder = configuration.jwtDecoder(signingKey, properties, clock);
        Jwt decoded = decoder.decode(issued.value());

        assertEquals("HS256", decoded.getHeaders().get("alg"));
        assertEquals("JWT", decoded.getHeaders().get("typ"));
        assertEquals(properties.issuer(), decoded.getIssuer().toString());
        assertEquals(USER_ID.toString(), decoded.getSubject());
        assertEquals(NOW, decoded.getIssuedAt());
        assertEquals(NOW.plusSeconds(3600), decoded.getExpiresAt());
        assertEquals(3600, issued.expiresInSeconds());
        assertFalse(issued.toString().contains(issued.value()));
        assertFalse(properties.toString().contains(SECRET));
    }

    @Test
    void rejectsExpiredToken() {
        Clock issuanceClock = Clock.fixed(NOW, ZoneOffset.UTC);
        AccessTokenService service = new AccessTokenService(
                configuration.jwtEncoder(signingKey), properties, issuanceClock);
        String token = service.issue(USER_ID).value();
        Clock afterExpiry = Clock.fixed(NOW.plusSeconds(3601), ZoneOffset.UTC);

        assertThrows(JwtException.class,
                () -> configuration.jwtDecoder(signingKey, properties, afterExpiry).decode(token));
    }

    @Test
    void rejectsSignedTokenWithoutRequiredLifetimeClaims() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtClaimsSet incompleteClaims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(USER_ID.toString())
                .issuedAt(NOW)
                .id(UUID.randomUUID().toString())
                .build();
        String token = configuration.jwtEncoder(signingKey)
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), incompleteClaims))
                .getTokenValue();

        assertThrows(JwtException.class,
                () -> configuration.jwtDecoder(signingKey, properties, clock).decode(token));
    }

    @Test
    void rejectsSignedTokenWithNonFrozenLifetime() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtClaimsSet overlongClaims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(USER_ID.toString())
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(7200))
                .id(UUID.randomUUID().toString())
                .build();
        String token = configuration.jwtEncoder(signingKey)
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), overlongClaims))
                .getTokenValue();

        assertThrows(JwtException.class,
                () -> configuration.jwtDecoder(signingKey, properties, clock).decode(token));
    }

    @Test
    void rejectsTamperedSignature() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AccessTokenService service = new AccessTokenService(
                configuration.jwtEncoder(signingKey), properties, clock);
        String token = service.issue(USER_ID).value();
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement
                + token.substring(signatureStart + 1);

        assertThrows(JwtException.class,
                () -> configuration.jwtDecoder(signingKey, properties, clock).decode(tampered));
    }

    @Test
    void rejectsTokenFromDifferentIssuer() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AccessTokenService service = new AccessTokenService(
                configuration.jwtEncoder(signingKey), properties, clock);
        String token = service.issue(USER_ID).value();
        JwtProperties otherIssuer = new JwtProperties(
                "https://other-issuer.invalid", SECRET, Duration.ofMinutes(60));

        assertThrows(JwtException.class,
                () -> configuration.jwtDecoder(signingKey, otherIssuer, clock).decode(token));
    }

    @Test
    void rejectsWeakSecretAndNonFrozenLifetime() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtProperties("https://issuer.invalid", "too-short", Duration.ofMinutes(60)));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtProperties("https://issuer.invalid", SECRET, Duration.ofMinutes(30)));
    }
}
