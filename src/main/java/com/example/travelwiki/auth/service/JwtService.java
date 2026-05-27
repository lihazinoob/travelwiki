package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.entity.User;

/**
 * Issues signed backend JWT access tokens.
 *
 * <p>Access tokens are short-lived (configured via {@code auth.jwt.access-token-ttl-minutes})
 * and are verified by the JWT authentication filter on every protected request.
 * They are stateless — revocation requires waiting for natural expiry. Use the
 * refresh token service for server-side session control.
 */
public interface JwtService {

    /**
     * Signs and returns a new access token for the given user.
     *
     * <p>Claims embedded in the token: {@code sub} (user ID as string), {@code email},
     * {@code iat} (issued-at), {@code exp} (expiry).
     *
     * @param user the authenticated application user; must have a non-null ID
     * @return the compact JWT string and its expiry timestamp
     */
    JwtToken generateAccessToken(User user);
}
