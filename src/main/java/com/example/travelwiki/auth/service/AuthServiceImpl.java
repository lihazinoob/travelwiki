package com.example.travelwiki.auth.service;

import com.example.travelwiki.auth.dto.VerifiedGoogleToken;
import com.example.travelwiki.auth.entity.AuthProvider;
import com.example.travelwiki.auth.entity.User;
import com.example.travelwiki.auth.entity.UserAuthIdentity;
import com.example.travelwiki.auth.entity.UserStatus;
import com.example.travelwiki.auth.exception.UserSuspendedException;
import com.example.travelwiki.auth.repository.UserAuthIdentityRepository;
import com.example.travelwiki.auth.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production-grade implementation of {@link AuthService}.
 *
 * <h3>Transaction strategy</h3>
 * The entire find-or-create operation runs inside a single database transaction.
 * The {@code User} INSERT and the {@code UserAuthIdentity} INSERT are committed
 * atomically — either both succeed or neither is visible to other sessions.
 * JPA dirty-tracking flushes all changes to managed entities automatically when
 * the transaction commits, so no explicit {@code save()} is needed for fields
 * mutated on an already-persisted entity (e.g. {@code lastLoginAt}).
 *
 * <h3>Concurrent registration</h3>
 * If two requests for the same Google identity arrive simultaneously and both pass
 * the initial lookup (both see no existing row), only one can succeed in inserting
 * into {@code user_auth_identities} because of the unique constraint on
 * {@code (provider, provider_subject)}. The losing request will receive a
 * {@code DataIntegrityViolationException}, which propagates as HTTP 500 until a
 * future enhancement introduces a dedicated retry mechanism.
 *
 * <p>The correct fix is a {@code @Transactional(propagation = REQUIRES_NEW)}
 * wrapped insert in a separate Spring bean, so the failing transaction can be
 * independently rolled back and a fresh lookup performed in a new transaction.
 * This is deferred because simultaneous first-time sign-ins for the same Google
 * user are extremely unlikely in any realistic traffic pattern.
 *
 * <h3>Profile sync policy</h3>
 * On every sign-in, the user's Google display name and avatar URL are synced from
 * the verified token. This keeps the profile current if the user updates their
 * Google account. When a future release adds user-managed profile editing, this
 * policy must be revisited so that user-set values are not silently overwritten.
 *
 * <h3>Logging and PII</h3>
 * Email addresses, Google subject claims, tokens, and other PII are never written
 * to logs. Only opaque user IDs are emitted at INFO/WARN level, making logs safe
 * to ship to external collectors without prior redaction.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository userAuthIdentityRepository;

    public AuthServiceImpl(
        UserRepository userRepository,
        UserAuthIdentityRepository userAuthIdentityRepository
    ) {
        this.userRepository = userRepository;
        this.userAuthIdentityRepository = userAuthIdentityRepository;
    }

    // -------------------------------------------------------------------------
    // Public contract
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Marked {@code @Transactional} so that reads and writes within the call
     * share one database session and commit or roll back together.
     */
    @Override
    @Transactional
    public AuthUserResult findOrCreateGoogleUser(VerifiedGoogleToken verifiedToken) {
        Optional<UserAuthIdentity> existingIdentity = userAuthIdentityRepository
            .findByProviderAndProviderSubject(AuthProvider.GOOGLE, verifiedToken.subject());

        if (existingIdentity.isPresent()) {
            return signInExistingUser(existingIdentity.get(), verifiedToken);
        }

        return registerNewUser(verifiedToken);
    }

    // -------------------------------------------------------------------------
    // Existing-user path
    // -------------------------------------------------------------------------

    /**
     * Handles a sign-in for a user whose Google identity is already in the system.
     *
     * <p>{@code identity.getUser()} triggers a JPA lazy-load here. This is safe
     * because the call is inside the {@code @Transactional} boundary and the
     * persistence context is still open. Mutations to the managed {@code User}
     * and {@code UserAuthIdentity} entities will be flushed automatically when the
     * transaction commits — no explicit {@code save()} is needed.
     */
    private AuthUserResult signInExistingUser(
        UserAuthIdentity identity,
        VerifiedGoogleToken verifiedToken
    ) {
        User user = identity.getUser();

        enforceAccountIsActive(user);

        // Always update last-login on every successful sign-in.
        user.setLastLoginAt(OffsetDateTime.now());

        // Sync profile fields from Google on each sign-in so that the stored values
        // stay current if the user updates their Google profile photo or display name.
        syncGoogleProfileFields(user, verifiedToken);

        // Record the email presented at this specific auth event.
        // Google email can change in rare circumstances; this column gives an audit
        // trail without requiring users.email to change.
        identity.setEmailAtAuthTime(verifiedToken.email());

        LOGGER.info("Returning user signed in. userId={}", user.getId());
        return new AuthUserResult(user, false);
    }

    // -------------------------------------------------------------------------
    // New-user path
    // -------------------------------------------------------------------------

    /**
     * Handles the first-ever sign-in for a Google identity not yet in the system.
     *
     * <p>Inserts a {@link User} row and a linked {@link UserAuthIdentity} row within
     * the same transaction. Both must succeed together; if either fails, the whole
     * transaction is rolled back.
     */
    private AuthUserResult registerNewUser(VerifiedGoogleToken verifiedToken) {
        User newUser = buildUserFromGoogleToken(verifiedToken);
        userRepository.save(newUser);

        UserAuthIdentity newIdentity = buildIdentityForUser(newUser, verifiedToken);
        userAuthIdentityRepository.save(newIdentity);

        LOGGER.info("New user registered. userId={}", newUser.getId());
        return new AuthUserResult(newUser, true);
    }

    // -------------------------------------------------------------------------
    // Account-status enforcement
    // -------------------------------------------------------------------------

    /**
     * Rejects sign-in attempts for accounts that are not in {@link UserStatus#ACTIVE} state.
     *
     * <p>Only the user ID is logged here; no email or other PII is included.
     *
     * @throws UserSuspendedException if the account is suspended or deleted
     */
    private void enforceAccountIsActive(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            LOGGER.warn("Sign-in blocked: account is suspended. userId={}", user.getId());
            throw new UserSuspendedException(
                "Your account has been suspended. Please contact support."
            );
        }

        if (user.getStatus() == UserStatus.DELETED) {
            LOGGER.warn("Sign-in blocked: account is deleted. userId={}", user.getId());
            throw new UserSuspendedException(
                "This account is no longer available."
            );
        }
    }

    // -------------------------------------------------------------------------
    // Profile sync
    // -------------------------------------------------------------------------

    /**
     * Copies display name and picture URL from the Google token onto the user entity.
     *
     * <p>Only non-null values from the token are applied. A missing claim in the
     * token leaves the existing stored value unchanged.
     */
    private void syncGoogleProfileFields(User user, VerifiedGoogleToken verifiedToken) {
        if (verifiedToken.displayName() != null) {
            user.setDisplayName(verifiedToken.displayName());
        }
        if (verifiedToken.pictureUrl() != null) {
            user.setPictureUrl(verifiedToken.pictureUrl());
        }
    }

    // -------------------------------------------------------------------------
    // Entity factory helpers
    // -------------------------------------------------------------------------

    /**
     * Constructs a transient {@link User} from verified Google token claims.
     *
     * <p>The returned entity has no ID yet. It becomes managed (and its ID is
     * populated by the DB sequence) only after {@code userRepository.save()} is called.
     */
    private User buildUserFromGoogleToken(VerifiedGoogleToken verifiedToken) {
        User user = new User();
        user.setEmail(verifiedToken.email());
        user.setEmailVerified(verifiedToken.emailVerified());
        user.setDisplayName(verifiedToken.displayName());
        user.setPictureUrl(verifiedToken.pictureUrl());
        // Registration and first sign-in are the same event in a Google-only auth flow,
        // so set lastLoginAt immediately.
        user.setLastLoginAt(OffsetDateTime.now());
        // status defaults to ACTIVE via the entity field initializer — no setter needed.
        return user;
    }

    /**
     * Constructs a transient {@link UserAuthIdentity} linking a user to their Google identity.
     *
     * <p>{@code user} must already be a managed entity (i.e. already saved, with a
     * non-null ID) before this method is called, so the foreign key constraint is satisfied.
     */
    private UserAuthIdentity buildIdentityForUser(User user, VerifiedGoogleToken verifiedToken) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.GOOGLE);
        identity.setProviderSubject(verifiedToken.subject());
        identity.setEmailAtAuthTime(verifiedToken.email());
        return identity;
    }
}
