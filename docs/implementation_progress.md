# Implementation Progress

This file is the single authoritative record of what has been built, why each decision was made, and what comes next. It should be read together with:

- `docs/task1_backend_spring_boot_guideline.md`

---

## Current project state

The project started as a bare Spring Boot backbone with an application entrypoint, PostgreSQL configuration, and Spring Data JPA dependency.

It has since grown to a production-oriented Google sign-in backend that can fully resolve a verified Google identity into a local application user. The auth pipeline is now complete up to the point of user resolution. JWT generation and refresh token issuance are the next two layers before the first real sign-in endpoint can return tokens to the Android client.

### Auth direction

- Android app uses Sign in with Google
- Android obtains a Google `idToken`
- Backend verifies the Google `idToken` cryptographically
- Backend finds or creates a local `User` record keyed on the stable Google `sub` claim
- Backend will issue its own short-lived access token and a server-stored, rotatable refresh token
- Android uses the backend access token for all protected endpoints

### Current pipeline position

```
[Android client]
     │  POST /api/v1/auth/google/verify   ← temporary verification-only endpoint
     │  { "idToken": "..." }
     ▼
[GoogleTokenVerificationService]          ← DONE: trust boundary, cryptographic verify
     │  VerifiedGoogleToken { sub, email, emailVerified, displayName, pictureUrl }
     ▼
[AuthService]                             ← DONE: resolves or creates local user
     │  AuthUserResult { user, newUser }
     ▼
[JwtService]                              ← NEXT: sign access token
[RefreshTokenService]                     ← NEXT: hash and persist refresh token
     │  AuthResponse { user, tokens, newUser }
     ▼
[Android client receives backend tokens]
```

---

## What has been implemented

### 1. Auth schema — V1 migration

File: `src/main/resources/db/migration/V1__create_auth_tables.sql`

Creates three tables:

**`users`**
- `id BIGSERIAL PRIMARY KEY`
- `email VARCHAR(255) NOT NULL UNIQUE`
- `display_name VARCHAR(255)`
- `picture_url TEXT`
- `status user_status NOT NULL DEFAULT 'ACTIVE'` — constrained enum: `ACTIVE`, `SUSPENDED`, `DELETED`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `last_login_at TIMESTAMPTZ`

**`user_auth_identities`**
- `id BIGSERIAL PRIMARY KEY`
- `user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `provider auth_provider NOT NULL` — constrained enum: `GOOGLE`
- `provider_subject VARCHAR(255) NOT NULL` — the Google `sub` claim
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`
- `UNIQUE(provider, provider_subject)` — the identity lookup key
- `UNIQUE(user_id, provider)` — one provider per user

**`refresh_tokens`**
- `id BIGSERIAL PRIMARY KEY`
- `user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `token_hash VARCHAR(255) NOT NULL UNIQUE` — SHA-256 hash; raw token never stored
- `expires_at TIMESTAMPTZ NOT NULL`
- `issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `is_revoked BOOLEAN NOT NULL DEFAULT FALSE`
- `revoked_at TIMESTAMPTZ`
- `replaced_by_token_id BIGINT REFERENCES refresh_tokens(id)` — rotation chain

Performance indexes created on `users(email)`, `user_auth_identities(user_id)`, and `refresh_tokens(user_id)`.

### 2. Auth schema — V2 migration

File: `src/main/resources/db/migration/V2__add_missing_auth_columns.sql`

V1 was missing two columns that were already mapped in the JPA entities. Running the application with `ddl-auto=none` and these columns absent would have caused runtime INSERT failures. V2 adds them non-destructively without touching V1 (preserving Flyway checksums).

```sql
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_auth_identities ADD COLUMN email_at_auth_time VARCHAR(255);
```

**Why `email_verified` on `users`:**
Google sign-in always produces verified emails (the verification service enforces `email_verified = true`). This flag is still surfaced explicitly on the entity so other auth providers or future flows with unverified emails can be distinguished cleanly.

**Why `email_at_auth_time` on `user_auth_identities`:**
Google email can change in rare circumstances. Recording the email as it was presented at each auth event gives a per-identity audit trail without requiring `users.email` to be updated on every sign-in.

### 3. JPA auth entities

Package: `src/main/java/com/example/travelwiki/auth/entity`

**`User`**
- Maps the `users` table
- `@PrePersist` sets `createdAt` and `updatedAt`
- `@PreUpdate` refreshes `updatedAt`
- Lazy `@OneToMany` to `UserAuthIdentity` and `RefreshToken`
- `status` defaults to `UserStatus.ACTIVE` at the field level

**`UserAuthIdentity`**
- Maps the `user_auth_identities` table
- Lazy `@ManyToOne` back to `User`
- Unique constraints declared both in the `@Table` annotation and the DDL

**`RefreshToken`**
- Maps the `refresh_tokens` table
- Column `issued_at` (not `created_at`) — named to reflect token issuance semantics, not generic entity creation
- `isRevoked boolean` mapped to `is_revoked` — fast revocation flag, redundant with `revokedAt IS NOT NULL` but kept for cheap indexed revocation checks
- `replacedByToken` self-referential lazy FK for the rotation chain

**`AuthProvider` (enum):** `GOOGLE`

**`UserStatus` (enum):** `ACTIVE`, `SUSPENDED`, `DELETED`

### 4. Repository layer

Package: `src/main/java/com/example/travelwiki/auth/repository`

**`UserRepository`**
- `findByEmail(String email)` — used if email-based fallback is ever needed

**`UserAuthIdentityRepository`**
- `findByProviderAndProviderSubject(AuthProvider provider, String providerSubject)` — the primary sign-in lookup, keyed on the stable Google `sub` claim

**`RefreshTokenRepository`**
- Present, awaiting implementation of the refresh token service

### 5. Auth DTOs

Package: `src/main/java/com/example/travelwiki/auth/dto`

**`GoogleAuthRequest`** — request body for auth endpoints
- `idToken` — not blank, max 4096 chars

**`VerifiedGoogleToken`** — internal record crossing from the verification service to the auth service; never serialized to JSON
- `subject` — Google `sub` claim
- `email`
- `emailVerified`
- `displayName`
- `pictureUrl`

**`AuthUserResponse`** — user object returned to the client
- `id`, `email`, `emailVerified`, `displayName`, `pictureUrl`, `status`

**`AuthTokenPairResponse`** — token object returned to the client
- `tokenType`, `accessToken`, `accessTokenExpiresAt`, `refreshToken`, `refreshTokenExpiresAt`

**`AuthResponse`** — final auth response wrapper
- `user` (`AuthUserResponse`)
- `tokens` (`AuthTokenPairResponse`)
- `newUser` boolean

### 6. Google token verification service

Package: `src/main/java/com/example/travelwiki/auth/service`  
Config: `src/main/java/com/example/travelwiki/auth/config/GoogleAuthProperties.java`  
Exception: `src/main/java/com/example/travelwiki/auth/exception/InvalidGoogleTokenException.java`

**`GoogleTokenVerificationService`** — interface: `verify(String idToken) → VerifiedGoogleToken`

**`GoogleTokenVerificationServiceImpl`** — implementation:
- Rejects blank tokens immediately
- Verifies the token cryptographically using the Google Java client library (`GoogleIdTokenVerifier`)
- Enforces allowed audiences from `auth.google.allowed-audiences`
- Enforces allowed issuers: `accounts.google.com`, `https://accounts.google.com`
- Requires non-empty `sub`, non-empty `email`, `email_verified = true`
- Maps verified payload claims into `VerifiedGoogleToken`
- Throws `InvalidGoogleTokenException` for all failure cases

**`GoogleAuthProperties`** — `@ConfigurationProperties(prefix = "auth.google")`
- `allowedAudiences` — list of accepted Google client IDs

Configuration key: `auth.google.allowed-audiences`  
Expected backing environment variable: `GOOGLE_ALLOWED_AUDIENCES`

### 7. Auth service — find or create user

Package: `src/main/java/com/example/travelwiki/auth/service`

**`AuthUserResult`** — internal record; not an API DTO
- `user` — the resolved or newly created `User` entity
- `newUser` — `true` if this was a first-ever sign-in, `false` for a returning user
- Carries the result from the auth service to the downstream JWT and refresh token services

**`AuthService`** — interface: `findOrCreateGoogleUser(VerifiedGoogleToken) → AuthUserResult`

**`AuthServiceImpl`** — full implementation (see design decisions section for rationale):

*Existing user path:*
1. Looks up `user_auth_identities` by `(provider=GOOGLE, provider_subject=sub)`
2. Triggers lazy-load of the linked `User` within the active transaction
3. Calls `enforceAccountIsActive(user)` — throws `UserSuspendedException` for `SUSPENDED` or `DELETED` accounts
4. Updates `user.lastLoginAt` to now
5. Syncs `displayName` and `pictureUrl` from the Google token (non-null values only)
6. Updates `identity.emailAtAuthTime` to the current token email
7. All mutations are on JPA-managed entities; dirty tracking flushes them automatically at transaction commit — no explicit `save()` is needed or called

*New user path:*
1. Builds a transient `User` from token claims (`email`, `emailVerified`, `displayName`, `pictureUrl`, `lastLoginAt=now`, `status=ACTIVE`)
2. Calls `userRepository.save(newUser)` — entity becomes managed, ID populated by DB sequence
3. Builds a transient `UserAuthIdentity` linking the new user to `(GOOGLE, sub, email)`
4. Calls `userAuthIdentityRepository.save(newIdentity)` — entity becomes managed
5. Both INSERTs commit atomically within the single `@Transactional` boundary

*Transaction design:*
- `@Transactional` with default `REQUIRED` propagation is declared on the public `findOrCreateGoogleUser` method
- Private helper methods (`signInExistingUser`, `registerNewUser`, etc.) run within the same transaction automatically
- Spring AOP intercepts `@Transactional` only on public methods; declaring it on private helpers would have no effect

*Concurrent registration — known deferred case:*
If two simultaneous first-time sign-in requests for the same Google identity both pass the initial lookup and both attempt a new `User` INSERT, the unique constraint `uq_auth_provider_subject` on `(provider, provider_subject)` will reject one at the DB level with a `DataIntegrityViolationException`. This exception propagates as HTTP 500 for now. The correct fix requires a separate `@Transactional(propagation = REQUIRES_NEW)` bean so the failing transaction can be rolled back independently and the surviving account found in a fresh transaction. This is deferred because simultaneous first-time sign-ins for the exact same Google user are essentially impossible in any realistic traffic pattern.

*Logging discipline:*
- `userId` (opaque integer) is logged at INFO and WARN level
- Email, Google `sub`, token strings, and any other PII are never written to logs
- Logs must be safe to ship to external collectors without redaction

### 8. Exception handling — full current state

Package: `src/main/java/com/example/travelwiki/common/exception`

**`ApiErrorResponse`** — record: `success`, `code`, `message`, `path`, `timestamp`

**`ApiErrorCode`** — enum:
- `VALIDATION_FAILED`
- `MALFORMED_REQUEST`
- `INVALID_GOOGLE_TOKEN`
- `USER_SUSPENDED`
- `INTERNAL_SERVER_ERROR`

**`GlobalExceptionHandler`** — `@RestControllerAdvice` handling:

| Exception | HTTP status | Error code | Message strategy |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` | First field error message from DTO annotation |
| `HttpMessageNotReadableException` | 400 | `MALFORMED_REQUEST` | Static safe message |
| `InvalidGoogleTokenException` | 401 | `INVALID_GOOGLE_TOKEN` | Generic safe message; detail logged at WARN |
| `UserSuspendedException` | 403 | `USER_SUSPENDED` | Message from exception (written to be client-safe); userId already logged in service |
| `Exception` (catch-all) | 500 | `INTERNAL_SERVER_ERROR` | Generic safe message; full exception logged at ERROR |

**`InvalidGoogleTokenException`** — unchecked, thrown by the verification service  
**`UserSuspendedException`** — unchecked, thrown by the auth service for `SUSPENDED` / `DELETED` accounts

### 9. Spring Security configuration

File: `src/main/java/com/example/travelwiki/config/SecurityConfig.java`

- CSRF disabled (API-only, no browser session)
- CORS enabled — currently permissive (`*`) for development; must be locked to the Android app origin before production
- Session policy: `STATELESS`
- Public routes: `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/error`
- All other routes require authentication (JWT filter not yet wired; this is structurally prepared)

### 10. Auth controller — current state

File: `src/main/java/com/example/travelwiki/auth/controller/AuthController.java`

Currently exposes one endpoint:

**`POST /api/v1/auth/google/verify`**
- Accepts `GoogleAuthRequest` with `@Valid`
- Calls `GoogleTokenVerificationService.verify()`
- Returns `VerifiedGoogleToken` directly

This endpoint is intentionally kept as a verification-only endpoint for development and debugging. It does not yet call `AuthService` or issue tokens. The full sign-in endpoint will be a separate route wired once both the JWT service and the refresh token service exist, so the client never receives a half-formed response.

### 11. Logging — current state

The project uses SLF4J (`Logger`/`LoggerFactory`) without Lombok.

Current logging coverage:
- `GlobalExceptionHandler` — WARN for Google token failures; ERROR for unexpected exceptions
- `AuthServiceImpl` — INFO for successful sign-ins and new registrations; WARN for blocked suspended/deleted accounts

Not yet implemented:
- Structured JSON logging
- Request-scoped log fields (correlation ID, request ID, user ID on authenticated routes)
- Log masking rules for sensitive values
- Centralized log shipping

---

## Design decisions

### Identity key: Google `sub`, not email

Email must never be the identity key for Google authentication. Google email addresses can change when a user updates their Google account. The `sub` claim is a permanent, stable, Google-assigned identifier for an account.

The lookup is always: `provider = GOOGLE AND provider_subject = <sub>`.

### `user_auth_identities` is separate from `users`

`users` represents the application-level user — who they are inside this system.
`user_auth_identities` represents an external identity linkage — how they proved their identity this time.

Keeping them separate:
- allows future Apple or Facebook login without changing the `users` schema
- makes account linking (multiple providers → one user) a natural join query
- keeps the auth provider's data (sub, email-at-auth-time) isolated from app-level user data

### Refresh tokens are hashed and stored in a table

Storing refresh tokens server-side (as SHA-256 hashes) enables:
- Explicit revocation (logout, security incident response)
- Rotation with replay detection (the `replaced_by_token_id` chain allows detecting a stolen rotated token)
- Per-device session visibility if needed later

Raw tokens never touch the database. Only the hash is persisted.

### `@Transactional` on the service method, not on private helpers

Spring AOP only intercepts `@Transactional` on public methods (it creates a proxy around the bean). Private methods always run within whatever transaction the calling public method started. Placing `@Transactional` on private helpers would compile and appear to work but would be silently ignored.

### JPA dirty tracking instead of explicit `save()` on managed entities

When an entity is loaded within a `@Transactional` boundary it becomes a managed entity attached to the JPA persistence context. Any field mutations on it are automatically detected (dirty tracking) and flushed to the database when the transaction commits. Calling `save()` on an already-managed entity is a no-op at the JPA level. The implementation relies on dirty tracking for updates (existing user path) and uses explicit `save()` only for genuinely new (transient) entities.

### `SUSPENDED` and `DELETED` both map to `UserSuspendedException` → 403

Returning different HTTP responses or different error messages for "suspended" vs "deleted" would allow an attacker to determine the exact state of any account. Both states receive the same 403 Forbidden status and `USER_SUSPENDED` error code. The client-facing message is human-friendly but reveals nothing about which state applies.

### Profile sync policy: always sync from Google on sign-in

Display name and picture URL are overwritten from the Google token on every successful sign-in. This keeps the stored profile current if the user updates their Google account without requiring any additional server call. Only non-null token values are applied so that missing claims do not overwrite stored values with null.

This policy must be re-evaluated when user-managed profile editing is added, to avoid silently overwriting values the user has set themselves.

### PII-safe logging

Google `idToken` values, refresh tokens, access tokens, Google `sub` claims, and email addresses must never appear in logs. The only safe identifier to log is the internal `userId` (an opaque database integer). This means logs are safe to ship to external collectors (Datadog, Elastic, etc.) without redaction pipelines.

---

## What has not been implemented yet

- JWT generation service
- Refresh token issuance service
- Refresh token rotation service
- Full sign-in endpoint returning `AuthResponse`
- JWT authentication filter
- Protected route authorization
- Global API response wrapper (success envelope)
- Production-grade observability:
  - Structured JSON logging
  - Request-scoped correlation ID
  - Log masking for sensitive fields
  - Actuator metrics and health endpoints
  - Centralized log shipping

---

## Immediate next step

### JWT generation service

`AuthService.findOrCreateGoogleUser()` now returns `AuthUserResult { user, newUser }`. The next layer is a `JwtService` that accepts the resolved `User` and produces a signed backend access token.

Recommended implementation direction:

- Add `jjwt` (io.jsonwebtoken) to `pom.xml`
- Read a signing key from configuration: `auth.jwt.secret` (HMAC-SHA256) or an RSA key pair
- Keep the secret out of `application.properties`; back it with `JWT_SECRET` or a secrets manager entry
- Access token claims: `sub` (user ID as string), `email`, `iat`, `exp`
- Keep access token TTL short: 15 minutes is standard
- Service contract: `generateAccessToken(User user) → JwtToken { tokenString, expiresAt }`
- Once `JwtService` exists, add `RefreshTokenService`, then wire both into the full sign-in endpoint

---

## Suggested next implementation order

1. ~~Auth service — find or create user~~ ✅ Done
2. JWT generation service
3. Refresh token service (issue, validate, rotate, revoke)
4. Full sign-in controller endpoint — `POST /api/v1/auth/google/signin` — returning `AuthResponse`
5. Global API success response wrapper (align success and error envelopes)
6. JWT authentication filter
7. Protected endpoint smoke test
8. Request logging with correlation ID
9. Actuator and metrics when deployment monitoring becomes relevant

---

## Auth flow — what is done vs what is pending

### What the server can do right now (end-to-end verified)

1. Android sends `POST /api/v1/auth/google/verify` with a Google `idToken`
2. Backend validates the `GoogleAuthRequest`
3. Backend verifies the Google token cryptographically
4. Backend finds or creates the local `User` (the `AuthService` is fully implemented)
5. ~~Backend returns `VerifiedGoogleToken`~~ — temporary response; full `AuthResponse` pending

### What the full flow will look like once tokens are implemented

1. Android sends `POST /api/v1/auth/google/signin` with a Google `idToken`
2. Backend validates `GoogleAuthRequest`
3. Backend verifies the Google token → `VerifiedGoogleToken`
4. Backend finds or creates the local user → `AuthUserResult`
5. Backend signs a short-lived JWT access token → `accessToken`
6. Backend generates, hashes, and stores a refresh token → `refreshToken`
7. Backend returns `AuthResponse { user, tokens { accessToken, refreshToken, ... }, newUser }`
8. Android stores both tokens; uses `accessToken` as `Authorization: Bearer <token>` on protected endpoints
9. Android uses `refreshToken` at expiry to get a new token pair

---

## Risks and open issues

### 1. Concurrent registration edge case (deferred)

If two sign-in requests for the exact same new Google user arrive in the same millisecond, the second INSERT will fail with a `DataIntegrityViolationException` from the unique constraint. This propagates as HTTP 500 until the REQUIRES_NEW retry pattern is implemented. The risk in a travel wiki app with real traffic is effectively zero.

### 2. CORS is permissive in development

`SecurityConfig` currently allows `*` origins. This must be locked to the Android app's origin (or removed for mobile-only APIs where CORS is irrelevant) before any production deployment.

### 3. Google audience configuration must be correct

`auth.google.allowed-audiences` must match the Google client IDs used by the Android app. A mismatch will reject every valid sign-in silently at the verification layer.

### 4. JWT secret must not be in source control

When `JwtService` is added, the signing key must come from an environment variable or secrets manager — never committed to `application.properties` or version control.

### 5. Datasource password is hardcoded in `application.properties`

`spring.datasource.password` currently contains a plain-text password committed to the repository. This must be replaced with `${DB_PASSWORD}` backed by an environment variable or a secrets manager before any code is shared outside this local development environment.

### 6. Logging is not production-grade yet

Missing:
- Structured JSON output
- Correlation ID attached to every log line in a request
- Sensitive-value masking
- Centralized log shipping
- Metrics and alerting

---

## Progress summary

| Item | Status |
|---|---|
| Auth database schema (V1) | ✅ Done |
| Schema alignment fix (V2) | ✅ Done |
| JPA auth entities | ✅ Done |
| Auth repositories | ✅ Done |
| Flyway integration | ✅ Done |
| Google auth request/response DTOs | ✅ Done |
| `VerifiedGoogleToken` internal DTO | ✅ Done |
| `AuthUserResult` internal service result | ✅ Done |
| Google token verification service | ✅ Done |
| Auth service — find or create user | ✅ Done |
| `UserSuspendedException` + 403 handling | ✅ Done |
| `ApiErrorCode.USER_SUSPENDED` | ✅ Done |
| Centralized exception handler | ✅ Done |
| Spring Security route configuration | ✅ Done |
| Verification-only controller endpoint | ✅ Done (temporary) |
| SLF4J logging in exception handler and auth service | ✅ Done |
| JWT generation service | ⏳ Next |
| Refresh token service | ⏳ Pending |
| Full sign-in endpoint (`AuthResponse`) | ⏳ Pending |
| JWT authentication filter | ⏳ Pending |
| Protected endpoint testing | ⏳ Pending |
| Global API success response wrapper | ⏳ Pending |
| Structured logging + correlation ID | ⏳ Pending |
| Actuator + metrics | ⏳ Pending |
