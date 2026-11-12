package com.ticketing.platform.user.service;

import com.ticketing.platform.user.api.RegisterRequest;
import com.ticketing.platform.user.api.RegisterResponse;
import com.ticketing.platform.user.domain.Role;
import com.ticketing.platform.user.domain.User;
import com.ticketing.platform.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) throw new DuplicateEmailException();

        User user = new User(email, passwords.encode(request.password()),
                request.fullName(), Role.USER);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateEmail(ex)) {
                throw new DuplicateEmailException();
            }
            throw ex;
        }
        return new RegisterResponse(user.getId(), user.getEmail(),
                user.getFullName(), user.getRole());
    }

    private boolean isDuplicateEmail(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uq_users_email".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
