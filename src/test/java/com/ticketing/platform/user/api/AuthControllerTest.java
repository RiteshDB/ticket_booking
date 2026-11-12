package com.ticketing.platform.user.api;

import com.ticketing.platform.common.api.GlobalExceptionHandler;
import com.ticketing.platform.security.SecurityConfig;
import com.ticketing.platform.user.domain.Role;
import com.ticketing.platform.user.service.AuthService;
import com.ticketing.platform.user.service.DuplicateEmailException;
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
}
