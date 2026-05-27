package com.example.travelwiki.auth.service;

import java.time.OffsetDateTime;

/**
 * Internal result from {@link RefreshTokenService#issueRefreshToken}.
 *
 * <p>Carries the <em>raw</em> (unhashed) refresh token string and its expiry.
 * This is the only point in the system where the raw token is available — it is
 * returned to the controller so it can be sent to the client, then discarded.
 * Only the SHA-256 hash is persisted to the database.
 *
 * <p>This is not an API DTO — it is never serialized to JSON directly.
 */
public record RefreshTokenResult(String rawToken, OffsetDateTime expiresAt) {
}
