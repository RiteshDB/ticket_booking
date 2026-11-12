package com.ticketing.platform.user.api;

import com.ticketing.platform.user.domain.Role;
import java.util.UUID;

public record RegisterResponse(UUID id, String email, String fullName, Role role) {
}
