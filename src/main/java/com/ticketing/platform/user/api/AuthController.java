package com.ticketing.platform.user.api;

import com.ticketing.platform.user.service.AuthService;
import com.ticketing.platform.user.service.LoginService;
import com.ticketing.platform.user.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginService loginService;
    private final RefreshTokenService sessions;

    public AuthController(AuthService authService, LoginService loginService,
                          RefreshTokenService sessions) {
        this.authService = authService;
        this.loginService = loginService;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(loginService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(sessions.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        sessions.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
