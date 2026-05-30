package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.entity.User;

/**
 * Internal result from {@link RefreshTokenService#rotate}.
 *
 * <p>Carries the resolved {@link User} (for access token generation) and the
 * newly issued {@link RefreshTokenResult}. Neither field is serialized to JSON —
 * the controller assembles the final {@code AuthResponse} from these values.
 */
public record RefreshTokenRotationResult(User user, RefreshTokenResult newRefreshToken) {
}
