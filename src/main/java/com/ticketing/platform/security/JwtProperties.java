package com.ticketing.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl
) {
    public JwtProperties {
        if (accessTokenTtl != null && (accessTokenTtl.isZero()
                || accessTokenTtl.isNegative()
                || accessTokenTtl.compareTo(Duration.ofHours(1)) > 0)) {
            throw new IllegalArgumentException("JWT lifetime must be positive and at most one hour");
        }
    }
}

