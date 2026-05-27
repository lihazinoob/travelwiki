package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.entity.User;

/**
 * Manages server-side refresh token lifecycle.
 *
 * <p>Refresh tokens are stored as SHA-256 hashes. The raw token is generated here,
 * returned once to the caller (so it can be sent to the client), and then discarded —
 * it is never stored in plain text. Storing only the hash means a database breach
 * cannot be replayed against the token endpoint.
 */
public interface RefreshTokenService {

    /**
     * Generates a new refresh token for the given user, hashes it, and persists the
     * hash to the {@code refresh_tokens} table.
     *
     * @param user the authenticated application user; must have a non-null ID
     * @return the raw (unhashed) token string and its expiry timestamp; the raw token
     *         must be forwarded to the client and is not retrievable afterwards
     */
    RefreshTokenResult issueRefreshToken(User user);
}
