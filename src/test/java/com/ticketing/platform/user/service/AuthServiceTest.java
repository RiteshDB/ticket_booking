package com.ticketing.platform.user.service;

import com.ticketing.platform.user.api.RegisterRequest;
import com.ticketing.platform.user.domain.Role;
import com.ticketing.platform.user.domain.User;
import com.ticketing.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final Pbkdf2PasswordEncoder passwords =
            Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final AuthService service = new AuthService(users, passwords);
    private final RegisterRequest request = new RegisterRequest(
            "Developer@Example.com", "a-long-demo-password", "Demo Developer");

    @Test
    void normalizesEmailHashesPasswordAndAssignsUserRole() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertThat(user.getEmail()).isEqualTo("developer@example.com");
            assertThat(user.getRole()).isEqualTo(Role.USER);
            assertThat(user.getPasswordHash()).isNotEqualTo(request.password());
            assertThat(user.getPasswordHash()).hasSizeLessThanOrEqualTo(100);
            assertThat(passwords.matches(request.password(), user.getPasswordHash())).isTrue();
            return user;
        });
        assertThat(service.register(request).email()).isEqualTo("developer@example.com");
        verify(users).saveAndFlush(any(User.class));
    }

    @Test
    void rejectsKnownDuplicateBeforeSaving() {
        when(users.existsByEmail("developer@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void translatesEmailConstraintFailureWhenPrecheckMissesRace() {
        var constraint = new org.hibernate.exception.ConstraintViolationException(
                "duplicate", new SQLException(), "uq_users_email");
        when(users.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", constraint));
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void doesNotMislabelOtherDatabaseFailuresAsDuplicateEmails() {
        var failure = new DataIntegrityViolationException("some other constraint");
        when(users.saveAndFlush(any(User.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.register(request)).isSameAs(failure);
    }
}

