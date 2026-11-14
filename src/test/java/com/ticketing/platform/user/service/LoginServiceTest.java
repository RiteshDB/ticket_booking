package com.ticketing.platform.user.service;

import com.ticketing.platform.user.api.LoginRequest;
import com.ticketing.platform.user.api.SessionResponse;
import com.ticketing.platform.user.domain.User;
import com.ticketing.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenService sessions = mock(RefreshTokenService.class);
    private final Pbkdf2PasswordEncoder passwords =
            Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final LoginService service = new LoginService(users, passwords, sessions);

    @Test
    void correctPasswordIssuesTokenForNormalizedEmail() {
        UUID id = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getPasswordHash()).thenReturn(passwords.encode("a-long-demo-password"));
        when(users.findByEmail("developer@example.com")).thenReturn(Optional.of(user));
        var expected = new SessionResponse("signed-token", "Bearer", 900,
                "refresh-token", java.time.Instant.now().plusSeconds(604800));
        when(sessions.startSession(id)).thenReturn(expected);

        assertThat(service.login(new LoginRequest(
                " Developer@Example.com ", "a-long-demo-password"))).isEqualTo(expected);
        verify(sessions).startSession(id);
    }

    @Test
    void wrongPasswordDoesNotIssueToken() {
        User user = mock(User.class);
        when(user.getPasswordHash()).thenReturn(passwords.encode("a-long-demo-password"));
        when(users.findByEmail("developer@example.com")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login(
                new LoginRequest("developer@example.com", "incorrect-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password.");
        verifyNoInteractions(sessions);
    }

    @Test
    void unknownEmailUsesSameErrorAndDoesNotIssueToken() {
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(
                new LoginRequest("unknown@example.com", "a-long-demo-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password.");
        verifyNoInteractions(sessions);
    }
}
