package com.ticketing.platform.user.api;

import com.ticketing.platform.common.api.GlobalExceptionHandler;
import com.ticketing.platform.security.SecurityConfig;
import com.ticketing.platform.user.domain.Role;
import com.ticketing.platform.user.service.AuthService;
import com.ticketing.platform.user.service.DuplicateEmailException;
import com.ticketing.platform.user.service.LoginService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean LoginService loginService;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final String VALID = """
            {"email":"developer@example.com","password":"a-long-demo-password",
             "fullName":"Demo Developer"}
            """;

    @Test
    void registersWithoutAuthentication() throws Exception {
        when(authService.register(any())).thenReturn(new RegisterResponse(
                UUID.randomUUID(), "developer@example.com", "Demo Developer", Role.USER));
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void rejectsInvalidInputBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bad","password":"short","fullName":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(authService);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateEmailException());
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTokenAndDisablesCaching() throws Exception {
        when(loginService.login(any())).thenReturn(new TokenResponse("signed-token", "Bearer", 900));
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void badCredentialsReturnUnauthorizedProblem() throws Exception {
        when(loginService.login(any())).thenThrow(
                new org.springframework.security.authentication.BadCredentialsException(
                        "Invalid email or password."));
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void invalidLoginInputDoesNotCallService() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(loginService);
    }
}

