package com.ticketing.platform.user.service;

import com.ticketing.platform.security.TokenService;
import com.ticketing.platform.user.api.LoginRequest;
import com.ticketing.platform.user.api.TokenResponse;
import com.ticketing.platform.user.domain.User;
import com.ticketing.platform.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode("not-an-account-password");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        var user = users.findByEmail(User.normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwords.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        return tokens.issueAccessToken(user.get().getId());
    }
}

