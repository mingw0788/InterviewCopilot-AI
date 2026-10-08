package com.interviewcopilot.business.auth;

import com.interviewcopilot.business.security.AccessTokenService;
import com.interviewcopilot.business.user.CredentialPolicy;
import com.interviewcopilot.business.user.UserAccount;
import com.interviewcopilot.business.user.UserRepository;
import com.interviewcopilot.business.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final String dummyPasswordHash;

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        AccessTokenService accessTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("interviewcopilot-authentication-dummy");
    }

    public LoginResult login(LoginRequest request) {
        if (request == null) {
            throw new InvalidLoginRequestException("Login request is required");
        }

        String loginIdentifier;
        try {
            loginIdentifier = CredentialPolicy.normalizeLoginIdentifier(request.loginIdentifier());
            CredentialPolicy.validatePassword(request.password());
        } catch (CredentialPolicy.Violation violation) {
            throw new InvalidLoginRequestException(violation.getMessage());
        }

        UserAccount account = userRepository.findByLoginIdentifier(loginIdentifier).orElse(null);
        String hashToCheck = account == null ? dummyPasswordHash : account.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);
        if (account == null || !passwordMatches || account.status() != UserStatus.ACTIVE) {
            throw new AuthenticationFailedException();
        }

        AccessTokenService.IssuedAccessToken token = accessTokenService.issue(account.id());
        return new LoginResult(
                token.value(), token.expiresInSeconds(),
                new AuthenticatedUserView(account.id(), account.loginIdentifier(), account.status()));
    }

    public record LoginResult(String accessToken, long expiresIn, AuthenticatedUserView user) {
        @Override
        public String toString() {
            return "LoginResult[accessToken=<redacted>, expiresIn=" + expiresIn + ", user=" + user + "]";
        }
    }
}
