package com.example.travelwiki.auth.service;

import java.time.OffsetDateTime;

/**
 * Internal result from {@link JwtService#generateAccessToken}.
 *
 * <p>Carries both the compact JWT string and its expiry timestamp so callers
 * can populate {@code AuthTokenPairResponse} without re-parsing the token.
 *
 * <p>This is not an API DTO — it is never serialized to JSON directly.
 */
public record JwtToken(String tokenString, OffsetDateTime expiresAt) {
}
