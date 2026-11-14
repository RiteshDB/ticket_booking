package com.ticketing.platform.security;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.refresh")
public record RefreshTokenProperties(@NotNull Duration sessionTtl) {
    public RefreshTokenProperties {
        if (sessionTtl != null && (sessionTtl.isZero() || sessionTtl.isNegative()
                || sessionTtl.compareTo(Duration.ofDays(30)) > 0)) {
            throw new IllegalArgumentException("Refresh session TTL must be positive and at most 30 days");
        }
    }
}

