package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.entity.User;

/**
 * Internal service-layer result produced by the find-or-create user operation.
 *
 * <p>This is <em>not</em> an API DTO. It carries the resolved {@link User} and a flag
 * that distinguishes a newly registered account from an existing returning user.
 * Downstream collaborators — the JWT generation service and the refresh token service
 * (both upcoming) — consume this result to produce the final {@code AuthResponse} that
 * the controller returns to the client.
 *
 * <h3>Design note</h3>
 * Keeping this as a dedicated result type, rather than returning {@code User} directly,
 * makes the {@code newUser} signal first-class without polluting the {@link User} entity
 * with transient request-scoped state. It also makes the service contract self-documenting:
 * callers know they always receive both pieces of information.
 *
 * @param user    the resolved or newly created application user; always non-null
 * @param newUser {@code true} if the user was just created during this sign-in attempt;
 *                {@code false} for a returning user
 */
public record AuthUserResult(
    User user,
    boolean newUser
) {
}
