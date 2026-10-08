package com.interviewcopilot.business.user;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RegistrationServiceTests {
    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final Instant now = Instant.parse("2026-10-07T00:00:00Z");
    private final RegistrationService service = new RegistrationService(
            repository, encoder, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void trimsIdentifierAndStoresOnlyAnArgon2idHash() {
        RegisteredUser user = service.register(new RegistrationRequest("  Candidate01  ", "secure-password"));

        assertEquals("Candidate01", user.loginIdentifier());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(now, user.createdAt());
        org.mockito.ArgumentCaptor<String> hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(user.id()), eq("Candidate01"), hash.capture(), eq(now));
        assertTrue(hash.getValue().startsWith("$argon2id$"));
        assertFalse(hash.getValue().contains("secure-password"));
        assertTrue(encoder.matches("secure-password", hash.getValue()));
        assertFalse(encoder.matches("wrong-password", hash.getValue()));
        assertFalse(new RegistrationRequest("candidate", "secure-password")
                .toString().contains("secure-password"));
    }

    @Test
    void rejectsInvalidIdentifiersAndPasswordsBeforeHashingOrPersistence() {
        for (RegistrationRequest request : new RegistrationRequest[] {
                null,
                new RegistrationRequest(null, "secure-password"),
                new RegistrationRequest("  ab  ", "secure-password"),
                new RegistrationRequest("x".repeat(51), "secure-password"),
                new RegistrationRequest("bad\nname", "secure-password"),
                new RegistrationRequest("candidate", null),
                new RegistrationRequest("candidate", "short"),
                new RegistrationRequest("candidate", "🔐".repeat(7)),
                new RegistrationRequest("candidate", "x".repeat(129))
        }) {
            assertThrows(InvalidRegistrationException.class, () -> service.register(request));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void mapsOnlyLoginIdentifierUniqueConstraintToConflict() {
        doThrow(new DuplicateKeyException("duplicate", new SQLException(
                "Duplicate entry for key 'users.uk_users_login_identifier'", "23000", 1062)))
                .when(repository).insert(any(), any(), any(), any());

        assertThrows(DuplicateRegistrationException.class,
                () -> service.register(new RegistrationRequest("candidate", "secure-password")));
    }

    @Test
    void doesNotMisreportOtherIntegrityFailuresAsDuplicateAccount() {
        doThrow(new DuplicateKeyException("duplicate", new SQLException(
                "Duplicate entry for key 'PRIMARY'", "23000", 1062)))
                .when(repository).insert(any(), any(), any(), any());

        assertThrows(DuplicateKeyException.class,
                () -> service.register(new RegistrationRequest("candidate", "secure-password")));
    }
}
