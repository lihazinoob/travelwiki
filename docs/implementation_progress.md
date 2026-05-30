# Implementation Progress

This file is the single authoritative record of what has been built, why each decision was made, and what comes next. It should be read together with:

- `docs/task1_backend_spring_boot_guideline.md`
- `docs/api_reference.md` — endpoint documentation for all implemented APIs

---

## Current project state

The project started as a bare Spring Boot backbone with an application entrypoint, PostgreSQL configuration, and Spring Data JPA dependency.

The complete authentication layer is now implemented end-to-end:

- Google sign-in verifies the idToken, resolves or creates the local user, and issues a JWT access token + rotatable refresh token
- The JWT authentication filter validates the access token on every protected request and populates the Spring Security context
- The refresh endpoint rotates the refresh token and issues a new access token when the old one expires

The next phase is application feature APIs — the first protected endpoint.

### Auth direction

- Android app uses Sign in with Google
- Android obtains a Google `idToken`
- Backend verifies the Google `idToken` cryptographically
- Backend finds or creates a local `User` record keyed on the stable Google `sub` claim
- Backend issues its own short-lived JWT access token and a server-stored, rotatable refresh token
- Android uses the backend access token for all protected endpoints
- When the access token expires, Android calls `/refresh` with the refresh token to get a new pair

### Current pipeline position

```
[Android client]
     │  POST /api/v1/auth/google/signin        ← DONE
     │  { "idToken": "..." }
     ▼
[GoogleTokenVerificationService]              ← DONE: cryptographic verify
     │  VerifiedGoogleToken
     ▼
[AuthService]                                 ← DONE: resolves or creates local user
     │  AuthUserResult { user, newUser }
     ▼
[JwtService]                                  ← DONE: signs HS256 access token (15 min TTL)
[RefreshTokenService]                         ← DONE: hashes and persists refresh token (30 day TTL)
     │  AuthResponse { user, tokens, newUser }
     ▼
[Android client receives backend tokens]      ← DONE

     │  GET /api/v1/... (protected)
     │  Authorization: Bearer <accessToken>
     ▼
[JwtAuthenticationFilter]                     ← DONE: validates token, sets SecurityContext
     │  userId principal in SecurityContextHolder
     ▼
[Protected controller]                        ← NEXT: first real feature endpoint

     │  POST /api/v1/auth/google/refresh
     │  { "refreshToken": "..." }
     ▼
[RefreshTokenService.rotate()]                ← DONE: validates, revokes old, issues new pair
     │  AuthResponse { user, tokens, newUser=false }
     ▼
[Android stores new token pair]               ← DONE
```

---

## What has been implemented

### 1. Auth schema — V1 migration

File: `src/main/resources/db/migration/V1__create_auth_tables.sql`

Creates three tables. **Important:** the original V1 used PostgreSQL native custom enum types (`CREATE TYPE user_status AS ENUM ...`, `CREATE TYPE auth_provider AS ENUM ...`). These were replaced with `VARCHAR` columns after a runtime failure — see the "Problems encountered" section for the full explanation.

**`users`**
- `id BIGSERIAL PRIMARY KEY`
- `email VARCHAR(255) NOT NULL UNIQUE`
- `display_name VARCHAR(255)`
- `picture_url TEXT`
- `status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE'` — stores `ACTIVE`, `SUSPENDED`, or `DELETED` as a plain string
- `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `last_login_at TIMESTAMPTZ`

**`user_auth_identities`**
- `id BIGSERIAL PRIMARY KEY`
- `user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `provider VARCHAR(50) NOT NULL` — stores `GOOGLE` as a plain string
- `provider_subject VARCHAR(255) NOT NULL` — the Google `sub` claim
- `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
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

V1 was missing two columns that were already mapped in the JPA entities. V2 adds them non-destructively without touching V1 (preserving Flyway checksums).

```sql
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_auth_identities ADD COLUMN email_at_auth_time VARCHAR(255);
```

**Why `email_verified` on `users`:** Google sign-in always produces verified emails (the verification service enforces `email_verified = true`). This flag is surfaced explicitly so other auth providers or future flows with unverified emails can be distinguished cleanly.

**Why `email_at_auth_time` on `user_auth_identities`:** Google email can change in rare circumstances. Recording the email as it was presented at each auth event gives a per-identity audit trail without requiring `users.email` to be updated on every sign-in.

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
- Column `issued_at` (not `created_at`) — named to reflect token issuance semantics
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
- `findByTokenHash(String tokenHash)` — used by the rotation service to look up a token by its SHA-256 hash

### 5. Auth DTOs

Package: `src/main/java/com/example/travelwiki/auth/dto`

**`GoogleAuthRequest`** — request body for sign-in
- `idToken` — not blank, max 4096 chars

**`RefreshRequest`** — request body for token refresh
- `refreshToken` — not blank, max 512 chars

**`VerifiedGoogleToken`** — internal record; never serialized to JSON
- `subject`, `email`, `emailVerified`, `displayName`, `pictureUrl`

**`AuthUserResponse`** — user object returned to the client
- `id`, `email`, `emailVerified`, `displayName`, `pictureUrl`, `status`

**`AuthTokenPairResponse`** — token object returned to the client
- `tokenType`, `accessToken`, `accessTokenExpiresAt`, `refreshToken`, `refreshTokenExpiresAt`

**`AuthResponse`** — final auth response wrapper
- `user` (`AuthUserResponse`), `tokens` (`AuthTokenPairResponse`), `newUser` boolean

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
- Throws `InvalidGoogleTokenException` for all failure cases

**`GoogleAuthProperties`** — `@ConfigurationProperties(prefix = "auth.google")`
- `allowedAudiences` — list of accepted Google client IDs

### 7. Auth service — find or create user

Package: `src/main/java/com/example/travelwiki/auth/service`

**`AuthUserResult`** — internal record: `user`, `newUser`

**`AuthService`** — interface: `findOrCreateGoogleUser(VerifiedGoogleToken) → AuthUserResult`

**`AuthServiceImpl`** — full implementation:

*Existing user path:*
1. Looks up `user_auth_identities` by `(provider=GOOGLE, provider_subject=sub)`
2. Calls `enforceAccountIsActive(user)` — throws `UserSuspendedException` for `SUSPENDED` or `DELETED` accounts
3. Updates `user.lastLoginAt`, syncs `displayName` and `pictureUrl`, updates `identity.emailAtAuthTime`
4. Dirty tracking flushes all mutations at transaction commit — no explicit `save()` needed

*New user path:*
1. Builds a transient `User` from token claims and saves it
2. Builds a transient `UserAuthIdentity` and saves it
3. Both INSERTs commit atomically within the single `@Transactional` boundary

*Transaction design:* `@Transactional` with default `REQUIRED` propagation on the public method. Spring AOP does not intercept private methods so `@Transactional` on private helpers would be silently ignored.

### 8. Exception handling — full current state

Package: `src/main/java/com/example/travelwiki/common/exception`

**`ApiErrorResponse`** — record: `success`, `code`, `message`, `path`, `timestamp`

**`ApiErrorCode`** — enum:
- `VALIDATION_FAILED`
- `MALFORMED_REQUEST`
- `INVALID_GOOGLE_TOKEN`
- `USER_SUSPENDED`
- `INVALID_REFRESH_TOKEN`
- `REFRESH_TOKEN_EXPIRED`
- `INTERNAL_SERVER_ERROR`

**`GlobalExceptionHandler`** — `@RestControllerAdvice` handling:

| Exception | HTTP status | Error code | Message strategy |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` | First field error message from DTO annotation |
| `HttpMessageNotReadableException` | 400 | `MALFORMED_REQUEST` | Static safe message |
| `InvalidGoogleTokenException` | 401 | `INVALID_GOOGLE_TOKEN` | Generic safe message; detail logged at WARN |
| `UserSuspendedException` | 403 | `USER_SUSPENDED` | Message from exception; userId already logged in service |
| `InvalidRefreshTokenException` | 401 | `INVALID_REFRESH_TOKEN` | Generic safe message; detail logged at WARN |
| `RefreshTokenExpiredException` | 401 | `REFRESH_TOKEN_EXPIRED` | Static message telling client to re-authenticate |
| `Exception` (catch-all) | 500 | `INTERNAL_SERVER_ERROR` | Generic safe message; full exception logged at ERROR |

**Exception classes:**
- `InvalidGoogleTokenException` — thrown by verification service
- `UserSuspendedException` — thrown by auth service and rotation service
- `InvalidRefreshTokenException` — thrown by rotation service when token not found or already revoked
- `RefreshTokenExpiredException` — thrown by rotation service when token is past its TTL

### 9. Spring Security configuration

File: `src/main/java/com/example/travelwiki/config/SecurityConfig.java`

- CSRF disabled (API-only, no browser session)
- CORS enabled — currently permissive (`*`) for development; must be locked before production
- Session policy: `STATELESS`
- Public routes: `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/error`
- All other routes require authentication
- `JwtAuthenticationFilter` registered before `UsernamePasswordAuthenticationFilter`

### 10. Auth controller — current state

File: `src/main/java/com/example/travelwiki/auth/controller/AuthController.java`

Base mapping: `@RequestMapping("/api/v1/auth/google")`

**`POST /api/v1/auth/google/signin`** — production sign-in (see section 15)

**`POST /api/v1/auth/google/refresh`** — token rotation (see section 17)

**`POST /api/v1/auth/google/verify`** — development-only debug route; verifies a Google idToken and returns the extracted claims without touching the database or issuing tokens

### 11. Logging — current state

The project uses SLF4J (`Logger`/`LoggerFactory`) without Lombok.

Current logging coverage:
- `GlobalExceptionHandler` — WARN for Google token failures and invalid refresh tokens; ERROR for unexpected exceptions
- `AuthServiceImpl` — INFO for successful sign-ins and new registrations; WARN for blocked suspended/deleted accounts
- `RefreshTokenServiceImpl` — INFO on issuance and rotation (userId only); WARN for revoked token replay and non-active account rotation attempts
- `JwtAuthenticationFilter` — DEBUG for JWT validation failures (not WARN because an expired token is a normal client-side event, not a security incident)

Not yet implemented:
- Structured JSON logging
- Request-scoped log fields (correlation ID, user ID on authenticated routes)
- Log masking rules for sensitive values
- Centralized log shipping

### 12. JWT configuration

File: `src/main/java/com/example/travelwiki/auth/config/JwtProperties.java`

`@ConfigurationProperties(prefix = "auth.jwt")` record.

Fields:
- `secret` — `@NotBlank`; backed by `${JWT_SECRET}` env var; dev fallback in `application.properties` is labeled as local-only
- `accessTokenTtlMinutes` — `@Min(1)`; defaults to `15`
- `refreshTokenTtlDays` — `@Min(1)`; defaults to `30`

The `JWT_SECRET` must be overridden with a cryptographically random value (32+ bytes) in every non-local environment.

### 13. JWT service

Package: `src/main/java/com/example/travelwiki/auth/service`
Dependency: `io.jsonwebtoken:jjwt-api:0.12.6` (compile), `jjwt-impl` and `jjwt-jackson` (runtime)

**`JwtToken`** — internal result record: `tokenString`, `expiresAt`

**`JwtService`** — interface:
- `generateAccessToken(User user) → JwtToken`
- `extractUserId(String token) → Long` — parses and validates a JWT; throws `JwtException` on any failure

**`JwtServiceImpl`** — implementation:
- Derives HMAC key from `JwtProperties.secret()` via `Keys.hmacShaKeyFor()`
- Token claims: `sub` = `user.id` as string, `email`, `iat`, `exp`
- `extractUserId` uses `Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token)` — jjwt auto-validates signature and expiry

### 14. Refresh token issuance service

Package: `src/main/java/com/example/travelwiki/auth/service`

**`RefreshTokenResult`** — internal result record: `rawToken`, `expiresAt`

**`RefreshTokenService`** — interface:
- `issueRefreshToken(User user) → RefreshTokenResult`
- `rotate(String rawToken) → RefreshTokenRotationResult`

**`RefreshTokenServiceImpl.issueRefreshToken`** — implementation:
- Generates 32 cryptographically random bytes via `SecureRandom`, encoded as URL-safe base64 → 43-char token with ~256 bits of entropy
- SHA-256 hashes the raw token; stores only the hash
- Uses `userRepository.getReferenceById()` to avoid detached-entity errors when called from a different transaction than the one that loaded the user

### 15. Full sign-in endpoint

File: `src/main/java/com/example/travelwiki/auth/controller/AuthController.java`

**`POST /api/v1/auth/google/signin`:**
1. Validates `GoogleAuthRequest`
2. Verifies Google token → `VerifiedGoogleToken`
3. Finds or creates local user → `AuthUserResult`
4. Generates JWT access token → `JwtToken`
5. Issues refresh token → `RefreshTokenResult`
6. Returns `AuthResponse`

The controller is intentionally not `@Transactional`. Steps 3 and 5 each manage their own transaction.

### 16. JWT authentication filter

File: `src/main/java/com/example/travelwiki/security/JwtAuthenticationFilter.java`

**`JwtAuthenticationFilter extends OncePerRequestFilter`** — runs exactly once per request:
- Reads the `Authorization` header; skips the filter entirely if absent or not starting with `Bearer `
- Calls `JwtService.extractUserId(token)` — jjwt validates signature and expiry atomically
- On success: sets `UsernamePasswordAuthenticationToken(userId, null, emptyList())` on `SecurityContextHolder` — Spring Security sees an authenticated user with the userId as principal
- On `JwtException` (expired, wrong signature, malformed): clears the context and passes through — Spring Security's authorization layer then rejects the request with 401 for protected routes
- The filter never writes to the response; it only populates or clears the security context

Registered in `SecurityConfig` with `.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`.

The `userId` principal placed in the security context is available to any controller via `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` cast to `Long`.

### 17. Refresh token rotation endpoint

File: `src/main/java/com/example/travelwiki/auth/controller/AuthController.java`
Service: `src/main/java/com/example/travelwiki/auth/service/RefreshTokenServiceImpl.java`

**`RefreshTokenRotationResult`** — internal result record: `user`, `newRefreshToken`

**`RefreshTokenServiceImpl.rotate(String rawToken)`** — full rotation logic within a single `@Transactional` boundary:

1. SHA-256 hash the incoming raw token; look up by hash in DB
   - Not found → `InvalidRefreshTokenException`
2. Check `isRevoked` — if true, log WARN (possible stolen token replay) and throw `InvalidRefreshTokenException`
3. Check `expiresAt` — if past, throw `RefreshTokenExpiredException`
4. Lazy-load the `User` within the transaction; check `status == ACTIVE`
   - Not active → `UserSuspendedException`
5. Revoke the old token: `setRevoked(true)`, `setRevokedAt(now)` — dirty tracking flushes the UPDATE at commit
6. Generate new raw token, hash it, persist the new `RefreshToken` row
7. Set `existing.setReplacedByToken(newToken)` — fills `replaced_by_token_id` FK, building the rotation chain
8. Return `RefreshTokenRotationResult { user, RefreshTokenResult { newRawToken, newExpiresAt } }`

**Rotation chain purpose:** `replaced_by_token_id` links each old token to its replacement. If a revoked token is ever presented again (step 2), the chain can be traversed to identify all tokens issued since the theft and revoke them in a security incident response.

**`POST /api/v1/auth/google/refresh`** — controller endpoint:
1. Validates `RefreshRequest`
2. Calls `refreshTokenService.rotate()` → `RefreshTokenRotationResult`
3. Calls `jwtService.generateAccessToken(user)` → new `JwtToken`
4. Assembles and returns `AuthResponse` with `newUser = false`

---

## Design decisions

### Identity key: Google `sub`, not email

Email must never be the identity key for Google authentication. The `sub` claim is a permanent, stable, Google-assigned identifier. Email addresses can change. The lookup is always: `provider = GOOGLE AND provider_subject = <sub>`.

### `user_auth_identities` is separate from `users`

`users` represents the application-level user. `user_auth_identities` represents an external identity linkage. Separation allows future Apple or Facebook login without changing the `users` schema, and makes account linking a natural join query.

### Refresh tokens are hashed and stored in a table

Storing refresh tokens server-side (as SHA-256 hashes) enables:
- Explicit revocation (logout, security incident response)
- Rotation with replay detection (the `replaced_by_token_id` chain)
- Per-device session visibility if needed later

Raw tokens never touch the database. Only the hash is persisted.

### `@Transactional` on the service method, not on private helpers

Spring AOP only intercepts `@Transactional` on public methods. Placing it on private helpers would compile and appear to work but would be silently ignored.

### JPA dirty tracking instead of explicit `save()` on managed entities

Entities loaded within a `@Transactional` boundary are managed. Field mutations are auto-detected and flushed at commit. `save()` is called only for genuinely transient (new) entities.

### `SUSPENDED` and `DELETED` both map to `UserSuspendedException` → 403

Returning different responses for the two states would allow attackers to enumerate account states. Both receive the same 403 and `USER_SUSPENDED` code.

### Profile sync policy: always sync from Google on sign-in

Display name and picture URL are overwritten from the Google token on every successful sign-in. Only non-null token values are applied. This policy must be re-evaluated when user-managed profile editing is added.

### PII-safe logging

Token values, Google `sub` claims, and email addresses never appear in logs. The only safe identifier to log is the internal `userId` (an opaque integer).

### VARCHAR over PostgreSQL native enum types for JPA-mapped columns

PostgreSQL native `ENUM` types cause a hard runtime failure with Hibernate's `@Enumerated(EnumType.STRING)` because Hibernate sends Java enum values as `character varying` JDBC parameters. PostgreSQL 18 refuses to compare a native enum column against a varchar without an explicit cast. Rule: always use `VARCHAR` in DDL for columns mapped by `@Enumerated(EnumType.STRING)`.

---

## What has not been implemented yet

- First protected feature API endpoint (unblocks protected endpoint testing)
- Global API success response wrapper (align success and error response envelopes)
- Production-grade observability:
  - Structured JSON logging
  - Request-scoped correlation ID
  - Log masking for sensitive fields
  - Actuator metrics and health endpoints
  - Centralized log shipping

---

## Suggested next implementation order

1. ~~Auth service — find or create user~~ ✅ Done
2. ~~JWT generation service~~ ✅ Done
3. ~~Refresh token service (issue)~~ ✅ Done
4. ~~Full sign-in controller endpoint~~ ✅ Done
5. ~~Fix Flyway migration auto-configuration~~ ✅ Done
6. ~~JWT authentication filter~~ ✅ Done
7. ~~Refresh token rotation endpoint~~ ✅ Done
8. **First protected feature API** ← NEXT (also unblocks protected endpoint testing)
9. Global API success response wrapper (align success and error envelopes)
10. Request logging with correlation ID
11. Actuator and metrics when deployment monitoring becomes relevant

---

## Auth flow — complete end-to-end

### Sign-in

1. Android sends `POST /api/v1/auth/google/signin` with a Google `idToken`
2. Backend verifies the Google token cryptographically → `VerifiedGoogleToken`
3. Backend finds or creates the local `User` → `AuthUserResult`
4. Backend signs a short-lived JWT access token → `JwtToken`
5. Backend generates, SHA-256 hashes, and persists a refresh token → `RefreshTokenResult`
6. Backend returns `AuthResponse { user, tokens { accessToken, refreshToken, ... }, newUser }`

### Protected request

7. Android sends `Authorization: Bearer <accessToken>` on every protected request
8. `JwtAuthenticationFilter` validates the token and sets the userId principal in `SecurityContextHolder`
9. Spring Security allows the request through to the controller

### Token refresh

10. Access token expires (15 min TTL) → Android gets 401 from a protected endpoint
11. Android sends `POST /api/v1/auth/google/refresh` with the refresh token in the request body
12. Backend validates, revokes the old refresh token, issues a new access token and a new refresh token
13. Android stores both new tokens, discards the old refresh token

---

## Problems encountered (kept for future context — delete once resolved)

### P1 — Flyway auto-configuration not triggering in Spring Boot 4.x

**Status:** Resolved (user fixed this independently).

**What was tried and failed before the fix:**
- `flyway-core` alone (BOM version) → no output
- Adding `flyway-database-postgresql` (BOM version) → still no output
- Removing `baseline-on-migrate=true` → no change
- Maven clean + reload + full stop/start → no change

**Workaround that was in place:** `spring.flyway.enabled=false`. Tables created manually in pgAdmin from V1 + V2 SQL.

---

### P2 — PostgreSQL native enum types incompatible with Hibernate / JPA

**Status:** Resolved.

**Observed symptom:**
```
ERROR: operator does not exist: auth_provider = character varying
```

**Root cause:** `CREATE TYPE ... AS ENUM (...)` columns cannot be compared against `character varying` JDBC parameters in PostgreSQL 18. Hibernate uses varchar parameters for `@Enumerated(EnumType.STRING)`.

**Fix applied:** Converted both columns to `VARCHAR` using `ALTER COLUMN ... TYPE VARCHAR USING ...::text`. Dropped both custom types. Rewrote V1 to use `VARCHAR` from the start.

**Rule established:** Never use `CREATE TYPE ... AS ENUM` for columns mapped by `@Enumerated(EnumType.STRING)`. Use `VARCHAR` in DDL.

---

## Risks and open issues

### 1. Concurrent registration edge case (deferred)

Simultaneous first-time sign-ins for the exact same Google identity will result in a `DataIntegrityViolationException` → HTTP 500. Requires a `REQUIRES_NEW` retry pattern to fix. Risk is effectively zero for real traffic.

### 2. CORS is permissive in development

`SecurityConfig` allows `*` origins. Must be locked to the Android app's origin before production.

### 3. Google audience configuration must be correct

`auth.google.allowed-audiences` must match the Google client IDs used by the Android app.

### 4. JWT secret must not be in source control

`auth.jwt.secret` is backed by `${JWT_SECRET}`. The `application.properties` fallback is a dev-only placeholder. The production value must come from an environment variable or secrets manager.

### 5. Datasource password is hardcoded in `application.properties`

`spring.datasource.password` must be replaced with `${DB_PASSWORD}` backed by an environment variable before any code is shared outside local development.

### 6. Logging is not production-grade yet

Missing: structured JSON output, correlation ID per request, sensitive-value masking, centralized log shipping, metrics and alerting.

---

## Progress summary

| Item | Status |
|---|---|
| Auth database schema (V1) — VARCHAR columns | ✅ Done |
| Schema alignment fix (V2) | ✅ Done |
| JPA auth entities | ✅ Done |
| Auth repositories | ✅ Done |
| Flyway integration | ✅ Done |
| Google auth request/response DTOs | ✅ Done |
| `RefreshRequest` DTO | ✅ Done |
| `VerifiedGoogleToken` internal DTO | ✅ Done |
| `AuthUserResult` internal service result | ✅ Done |
| `RefreshTokenRotationResult` internal service result | ✅ Done |
| Google token verification service | ✅ Done |
| Auth service — find or create user | ✅ Done |
| `UserSuspendedException` + 403 handling | ✅ Done |
| `InvalidRefreshTokenException` + 401 handling | ✅ Done |
| `RefreshTokenExpiredException` + 401 handling | ✅ Done |
| `ApiErrorCode` — all current codes | ✅ Done |
| Centralized exception handler | ✅ Done |
| Spring Security route configuration | ✅ Done |
| `JwtProperties` configuration record | ✅ Done |
| `jjwt` dependency (0.12.6) | ✅ Done |
| JWT service — generate + extract | ✅ Done |
| `JwtToken` internal result record | ✅ Done |
| Refresh token issuance service | ✅ Done |
| `RefreshTokenResult` internal result record | ✅ Done |
| Full sign-in endpoint — `POST /api/v1/auth/google/signin` | ✅ Done (end-to-end tested) |
| JWT authentication filter | ✅ Done |
| Refresh token rotation endpoint — `POST /api/v1/auth/google/refresh` | ✅ Done |
| Verification-only debug endpoint | ✅ Done (dev only) |
| SLF4J logging across all services | ✅ Done |
| **First protected feature API** | ⏳ Next |
| Protected endpoint testing | ⏳ Pending |
| Global API success response wrapper | ⏳ Pending |
| Structured logging + correlation ID | ⏳ Pending |
| Actuator + metrics | ⏳ Pending |
