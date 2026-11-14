package com.ticketing.platform.user.api;

import com.ticketing.platform.common.api.GlobalExceptionHandler;
import com.ticketing.platform.security.JwtConfig;
import com.ticketing.platform.security.SecurityConfig;
import com.ticketing.platform.security.TokenService;
import com.ticketing.platform.user.domain.Role;
import com.ticketing.platform.user.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtConfig.class, TokenService.class, GlobalExceptionHandler.class})
class UserControllerTest {
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @MockitoBean UserQueryService users;

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        org.hamcrest.Matchers.startsWith("Bearer")));
        verifyNoInteractions(users);
    }

    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void validSignedTokenSelectsCurrentUserFromSubject() throws Exception {
        UUID id = UUID.randomUUID();
        when(users.findCurrentUser(id)).thenReturn(
                new UserResponse(id, "developer@example.com", "Demo Developer", Role.USER));
        String token = tokens.issueAccessToken(id).accessToken();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(users).findCurrentUser(id);
    }
}
