package com.ticketing.platform.user.api;

import com.ticketing.platform.user.service.UserQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserQueryService users;

    public UserController(UserQueryService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findCurrentUser(UUID.fromString(jwt.getSubject()));
    }
}

