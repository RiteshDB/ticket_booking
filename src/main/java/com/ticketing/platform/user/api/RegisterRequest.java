package com.ticketing.platform.user.api;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 100) String fullName
) {
    public RegisterRequest {
        if (email != null) email = email.trim();
        if (fullName != null) fullName = fullName.trim();
    }
}