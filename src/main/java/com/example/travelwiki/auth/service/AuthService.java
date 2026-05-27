package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.dto.VerifiedGoogleToken;
import com.example.travelwiki.auth.exception.UserSuspendedException;

/**
 * Core authentication orchestration service.
 *
 * This service sits between the identity-verification layer (Google token verification)
 * and the token-issuance layer (JWT + refresh token generation). Its sole responsibility
 * is to resolve a verified external identity into a local application user.
 *
 * Position in the auth pipeline
 *
 *   [Client]
 *      │ POST /api/v1/auth/google/signin  (idToken)
 *      ▼
 *   [GoogleTokenVerificationService]   ← trust boundary: verify with Google
 *      │ VerifiedGoogleToken (trusted claims)
 *      ▼
 *   [AuthService]                      ← THIS SERVICE: resolve local identity
 *      │ AuthUserResult (User + isNewUser)
 *      ▼
 *   [JwtService]          (upcoming)
 *   [RefreshTokenService] (upcoming)
 *      │ AuthResponse
 *      ▼
 *   [Client receives access token + refresh token]
 *
 *
 * Why a separate interface
 * Declaring the contract as an interface keeps the controller and any future callers
 * (e.g. a test double, an alternative provider implementation) decoupled from the
 * concrete implementation. It also makes the boundary between layers explicit and
 * independently testable.
 */
public interface AuthService {

    /**
     * Resolves a verified Google identity to a local application user, creating
     * a new account if one does not already exist.
     *
     * The lookup key is the stable Google {@code sub} claim stored in
     * {@code user_auth_identities}. Email is not used as the identity key
     * because Google email addresses can change.
     *
     * On an existing match:
     *
     *   The account status is checked; suspended or deleted accounts are rejected.
     *   {@code last_login_at} is updated to the current time.
     *   Profile fields (display name, picture URL) are refreshed from Google.
     *   {@code email_at_auth_time} in the identity record is updated.
     *
     *
     * On a new match (first sign-in):
     *
     *   A User row is created with status = ACTIVE.
     *   A linked UserAuthIdentity row is created with*       provider = GOOGLE and provider_subject = sub
     *   Both writes are committed in the same transaction.
     *
     *
     * @param verifiedToken trusted Google identity claims; must not be null}
     * @return a result containing the resolved user and whether the account is brand new
     * @throws UserSuspendedException if the matched account is suspended or deleted
     */
    AuthUserResult findOrCreateGoogleUser(VerifiedGoogleToken verifiedToken);
}
