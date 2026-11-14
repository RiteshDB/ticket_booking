package com.ticketing.platform.user.api;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
