package com.example.travelwiki.auth.dto;

public record VerifiedGoogleToken(
    String subject,
    String email,
    boolean emailVerified,
    String displayName,
    String pictureUrl
) {
}
