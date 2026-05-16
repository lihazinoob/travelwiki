package com.example.travelwiki.auth.dto;

import java.time.OffsetDateTime;

public record AuthTokenPairResponse(
    String tokenType,
    String accessToken,
    OffsetDateTime accessTokenExpiresAt,
    String refreshToken,
    OffsetDateTime refreshTokenExpiresAt
) {
}
