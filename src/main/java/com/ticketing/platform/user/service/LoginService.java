package com.ticketing.platform.user.service;

import com.ticketing.platform.user.api.LoginRequest;
import com.ticketing.platform.user.api.SessionResponse;
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
    private final RefreshTokenService sessions;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwords, RefreshTokenService sessions) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.dummyHash = passwords.encode("not-an-account-password");
    }

    @Transactional
    public SessionResponse login(LoginRequest request) {
        var user = users.findByEmail(User.normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwords.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        return sessions.startSession(user.get().getId());
    }
}
