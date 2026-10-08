package com.interviewcopilot.business.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
public class RegistrationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Autowired
    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, Clock.systemUTC());
    }

    RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public RegisteredUser register(RegistrationRequest request) {
        if (request == null) {
            throw new InvalidRegistrationException("Registration request is required");
        }
        String loginIdentifier;
        try {
            loginIdentifier = CredentialPolicy.normalizeLoginIdentifier(request.loginIdentifier());
            CredentialPolicy.validatePassword(request.password());
        } catch (CredentialPolicy.Violation violation) {
            throw new InvalidRegistrationException(violation.getMessage());
        }

        UUID id = UUID.randomUUID();
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String passwordHash = passwordEncoder.encode(request.password());
        try {
            userRepository.insert(id, loginIdentifier, passwordHash, createdAt);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateLoginIdentifier(exception)) {
                throw new DuplicateRegistrationException();
            }
            throw exception;
        }
        return new RegisteredUser(id, loginIdentifier, UserStatus.ACTIVE, createdAt);
    }

    private boolean isDuplicateLoginIdentifier(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1062
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().toLowerCase(Locale.ROOT).contains("uk_users_login_identifier")) {
                return true;
            }
        }
        return false;
    }
}
