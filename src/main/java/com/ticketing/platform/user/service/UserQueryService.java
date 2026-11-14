package com.ticketing.platform.user.service;

import com.ticketing.platform.user.api.UserResponse;
import com.ticketing.platform.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class UserQueryService {
    private final UserRepository users;

    public UserQueryService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse findCurrentUser(UUID userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Account is no longer available."));
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
