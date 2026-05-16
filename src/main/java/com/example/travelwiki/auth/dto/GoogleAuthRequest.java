package com.example.travelwiki.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleAuthRequest(
    @NotBlank(message = "Google ID token is required")
    @Size(max = 4096, message = "Google ID token must not exceed 4096 characters")
    String idToken
) {
}
