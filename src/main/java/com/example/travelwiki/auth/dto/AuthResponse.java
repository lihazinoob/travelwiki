package com.example.travelwiki.auth.dto;

public record AuthResponse(
    AuthUserResponse user,
    AuthTokenPairResponse tokens,
    boolean newUser
) {
}
