package com.ticketing.platform.user.api;

import java.time.Instant;

public record SessionResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        Instant refreshExpiresAt
) {
    public static SessionResponse of(TokenResponse access, String refresh, Instant expiry) {
        return new SessionResponse(access.accessToken(), access.tokenType(),
                access.expiresIn(), refresh, expiry);
    }
}
