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

    RefreshTokenResult issueRefreshToken(User user);

    /**
     * Validates an incoming raw refresh token, rotates it, and returns the resolved
     * user together with the newly issued refresh token.
     *
     * <p>Rotation means the presented token is immediately revoked and a brand-new
     * token is issued in its place. The old token's row records the new token ID in
     * {@code replaced_by_token_id}, building a chain that enables replay detection.
     *
     * <p>Throws:
     * <ul>
     *   <li>{@link com.example.travelwiki.auth.exception.InvalidRefreshTokenException}
     *       — token not found or already revoked (possible theft)</li>
     *   <li>{@link com.example.travelwiki.auth.exception.RefreshTokenExpiredException}
     *       — token found but past its {@code expires_at}</li>
     *   <li>{@link com.example.travelwiki.auth.exception.UserSuspendedException}
     *       — account is suspended or deleted</li>
     * </ul>
     */
    RefreshTokenRotationResult rotate(String rawToken);
}
