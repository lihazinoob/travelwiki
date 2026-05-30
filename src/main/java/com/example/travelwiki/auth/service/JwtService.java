package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.entity.User;

public interface JwtService {

    JwtToken generateAccessToken(User user);

    /**
     * Parses and validates a compact JWT string, returning the embedded user ID.
     *
     * <p>Throws {@link io.jsonwebtoken.JwtException} (unchecked) for any failure:
     * expired token, wrong signature, malformed string, or missing subject claim.
     * Callers that need to handle token failure gracefully should catch that type.
     */
    Long extractUserId(String token);
}
