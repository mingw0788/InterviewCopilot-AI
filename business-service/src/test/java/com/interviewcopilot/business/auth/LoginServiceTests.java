package com.interviewcopilot.business.auth;

import com.interviewcopilot.business.security.AccessTokenService;
import com.interviewcopilot.business.user.UserAccount;
import com.interviewcopilot.business.user.UserRepository;
import com.interviewcopilot.business.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoginServiceTests {
    private static final UUID USER_ID = UUID.fromString("72a779e2-f8cf-47f0-9e7a-23b9c8a2a02f");
    private static final Instant CREATED_AT = Instant.parse("2026-10-08T00:00:00Z");

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private LoginService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("interviewcopilot-authentication-dummy")).thenReturn("dummy-hash");
        service = new LoginService(repository, passwordEncoder, accessTokenService);
    }

    @Test
    void normalizesIdentifierAndReturnsTokenForActiveUser() {
        UserAccount account = account(UserStatus.ACTIVE);
        when(repository.findByLoginIdentifier("Candidate01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secure-password", "stored-hash")).thenReturn(true);
        when(accessTokenService.issue(USER_ID)).thenReturn(new AccessTokenService.IssuedAccessToken(
                "signed.jwt.token", CREATED_AT, CREATED_AT.plusSeconds(3600)));

        LoginService.LoginResult result = service.login(
                new LoginRequest("  Candidate01  ", "secure-password"));

        assertEquals("signed.jwt.token", result.accessToken());
        assertEquals(3600, result.expiresIn());
        assertEquals(USER_ID, result.user().id());
        assertEquals(UserStatus.ACTIVE, result.user().status());
        assertFalse(result.toString().contains("signed.jwt.token"));
        assertFalse(new LoginRequest("candidate", "secure-password").toString().contains("secure-password"));
    }

    @Test
    void missingUserStillChecksPasswordAgainstDummyHash() {
        when(repository.findByLoginIdentifier("candidate")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("secure-password", "dummy-hash")).thenReturn(false);

        AuthenticationFailedException exception = assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("candidate", "secure-password")));

        assertEquals("Invalid login identifier or password", exception.getMessage());
        verify(passwordEncoder).matches("secure-password", "dummy-hash");
        verifyNoInteractions(accessTokenService);
    }

    @Test
    void wrongPasswordAndLockedUserUseSameErrorAndIssueNoToken() {
        UserAccount active = account(UserStatus.ACTIVE);
        when(repository.findByLoginIdentifier("candidate")).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);
        AuthenticationFailedException wrongPassword = assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("candidate", "wrong-password")));

        UserAccount locked = account(UserStatus.LOCKED);
        when(repository.findByLoginIdentifier("candidate")).thenReturn(Optional.of(locked));
        when(passwordEncoder.matches("secure-password", "stored-hash")).thenReturn(true);
        AuthenticationFailedException lockedUser = assertThrows(AuthenticationFailedException.class,
                () -> service.login(new LoginRequest("candidate", "secure-password")));

        assertEquals(wrongPassword.getMessage(), lockedUser.getMessage());
        verify(accessTokenService, never()).issue(USER_ID);
    }

    @Test
    void rejectsInvalidInputBeforeDatabaseLookup() {
        for (LoginRequest request : new LoginRequest[] {
                null,
                new LoginRequest(null, "secure-password"),
                new LoginRequest("ab", "secure-password"),
                new LoginRequest("candidate", null),
                new LoginRequest("candidate", "short"),
                new LoginRequest("candidate", "x".repeat(129))
        }) {
            assertThrows(InvalidLoginRequestException.class, () -> service.login(request));
        }
        verifyNoInteractions(repository);
        verifyNoInteractions(accessTokenService);
    }

    private UserAccount account(UserStatus status) {
        return new UserAccount(USER_ID, "Candidate01", "stored-hash", status, CREATED_AT);
    }
}
