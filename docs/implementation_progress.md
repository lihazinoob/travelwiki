# Implementation Progress

This file is the single authoritative record of what has been built, why each decision was made, and what comes next. It should be read together with:

- `docs/task1_backend_spring_boot_guideline.md`

---

## Current project state

The project started as a bare Spring Boot backbone with an application entrypoint, PostgreSQL configuration, and Spring Data JPA dependency.

It has since grown to a fully working Google sign-in backend. The complete auth pipeline from token verification through user resolution, JWT issuance, refresh token issuance, and the production sign-in endpoint is implemented and wired together. The next phase is the JWT authentication filter, which validates the access token on protected routes.

### Auth direction

- Android app uses Sign in with Google
- Android obtains a Google `idToken`
- Backend verifies the Google `idToken` cryptographically
- Backend finds or creates a local `User` record keyed on the stable Google `sub` claim
- Backend issues its own short-lived JWT access token and a server-stored, rotatable refresh token
- Android uses the backend access token for all protected endpoints

### Current pipeline position

```
[Android client]
     │  POST /api/v1/auth/google/signin   ← production sign-in endpoint (DONE)
     │  { "idToken": "..." }
     ▼
[GoogleTokenVerificationService]          ← DONE: trust boundary, cryptographic verify
     │  VerifiedGoogleToken { sub, email, emailVerified, displayName, pictureUrl }
     ▼
[AuthService]                             ← DONE: resolves or creates local user
     │  AuthUserResult { user, newUser }
     ▼
[JwtService]                              ← DONE: signs HS256 access token (15 min TTL)
[RefreshTokenService]                     ← DONE: hashes and persists refresh token (30 day TTL)
     │  AuthResponse { user, tokens, newUser }
     ▼
[Android client receives backend tokens]  ← DONE

     │  GET /api/v1/... (protected)
     │  Authorization: Bearer <accessToken>
     ▼
[JwtAuthenticationFilter]                 ← NEXT: validate access token on every protected request
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

Exposes two endpoints:

**`POST /api/v1/auth/google/signin`** — production sign-in route (see section 15)

**`POST /api/v1/auth/google/verify`** — development-only debug route
- Accepts `GoogleAuthRequest` with `@Valid`
- Calls `GoogleTokenVerificationService.verify()`
- Returns `VerifiedGoogleToken` directly (no database access, no token issuance)
- Retained for local debugging; not intended for client consumption

### 11. Logging — current state

The project uses SLF4J (`Logger`/`LoggerFactory`) without Lombok.

Current logging coverage:
- `GlobalExceptionHandler` — WARN for Google token failures; ERROR for unexpected exceptions
- `AuthServiceImpl` — INFO for successful sign-ins and new registrations; WARN for blocked suspended/deleted accounts
- `RefreshTokenServiceImpl` — INFO on every refresh token issuance (userId only, no token values)

Not yet implemented:
- Structured JSON logging
- Request-scoped log fields (correlation ID, request ID, user ID on authenticated routes)
- Log masking rules for sensitive values
- Centralized log shipping

### 12. JWT configuration

File: `src/main/java/com/example/travelwiki/auth/config/JwtProperties.java`

`@ConfigurationProperties(prefix = "auth.jwt")` record, picked up automatically by `@ConfigurationPropertiesScan` on the main class.

Fields:
- `secret` — `@NotBlank`; backed by `${JWT_SECRET}` env var; dev fallback in `application.properties` is clearly labeled as local-only
- `accessTokenTtlMinutes` — `@Min(1)`; defaults to `15`
- `refreshTokenTtlDays` — `@Min(1)`; defaults to `30`

Configuration keys added to `application.properties`:
```
auth.jwt.secret=${JWT_SECRET:dev-only-insecure-jwt-secret-change-before-any-deployment}
auth.jwt.access-token-ttl-minutes=15
auth.jwt.refresh-token-ttl-days=30
```

The `JWT_SECRET` must be overridden with a cryptographically random value (32+ bytes) in every environment that is not a developer's local machine. The dev fallback is long enough (> 32 bytes) for jjwt's HS256 key requirement.

### 13. JWT generation service

Package: `src/main/java/com/example/travelwiki/auth/service`
Dependency added to `pom.xml`: `io.jsonwebtoken:jjwt-api:0.12.6` (compile), `jjwt-impl` and `jjwt-jackson` (runtime)

**`JwtToken`** — internal result record (not an API DTO):
- `tokenString` — the compact JWT string
- `expiresAt` — `OffsetDateTime`, so callers can populate `AuthTokenPairResponse` without re-parsing the token

**`JwtService`** — interface: `generateAccessToken(User user) → JwtToken`

**`JwtServiceImpl`** — implementation:
- Derives an HMAC key from `JwtProperties.secret()` using `io.jsonwebtoken.security.Keys.hmacShaKeyFor(secretBytes)`
- jjwt auto-selects HS256 for a 32–47 byte key, HS384 for 48–63 bytes, HS512 for 64+ bytes
- Token claims: `sub` = `user.id` as string, `email`, `iat`, `exp`
- TTL from `JwtProperties.accessTokenTtlMinutes()`
- Returns `JwtToken { tokenString, expiresAt }`
- Stateless — no database interaction; safe to call outside a transaction

### 14. Refresh token service

Package: `src/main/java/com/example/travelwiki/auth/service`

**`RefreshTokenResult`** — internal result record (not an API DTO):
- `rawToken` — the unhashed token string; this is the only moment the raw value is available
- `expiresAt` — `OffsetDateTime`

**`RefreshTokenService`** — interface: `issueRefreshToken(User user) → RefreshTokenResult`

**`RefreshTokenServiceImpl`** — full implementation:
- Generates 32 cryptographically random bytes via `SecureRandom`, encoded as URL-safe base64 (no padding) → 43-character opaque token with ~256 bits of entropy
- SHA-256 hashes the raw token using `MessageDigest` (JDK built-in; no extra dependency)
- Stores the hash as a hex string in `refresh_tokens.token_hash`
- `expiresAt` = now + `JwtProperties.refreshTokenTtlDays()`
- Uses `userRepository.getReferenceById(user.getId())` to re-attach the user entity to the current transaction's persistence context before setting the FK — avoids Hibernate detached-entity issues when the user was loaded in a prior transaction
- `@Transactional` on `issueRefreshToken()` (REQUIRED propagation)
- Logs issuance at INFO level with `userId` only — raw token never logged

*Raw token lifecycle:* generated in service → returned in `RefreshTokenResult` → placed in `AuthResponse` → sent to client → `RefreshTokenResult` reference goes out of scope. The raw token is never stored anywhere on the server side.

### 15. Full sign-in endpoint

File: `src/main/java/com/example/travelwiki/auth/controller/AuthController.java`

**`POST /api/v1/auth/google/signin`** — the production sign-in route:
1. Validates `GoogleAuthRequest` with `@Valid`
2. Calls `GoogleTokenVerificationService.verify()` → `VerifiedGoogleToken`
3. Calls `AuthService.findOrCreateGoogleUser()` → `AuthUserResult { user, newUser }`
4. Calls `JwtService.generateAccessToken(user)` → `JwtToken`
5. Calls `RefreshTokenService.issueRefreshToken(user)` → `RefreshTokenResult`
6. Assembles and returns `AuthResponse { user: AuthUserResponse, tokens: AuthTokenPairResponse, newUser }`

`AuthTokenPairResponse` fields set: `tokenType = "Bearer"`, `accessToken`, `accessTokenExpiresAt`, `refreshToken` (raw), `refreshTokenExpiresAt`.

The controller is intentionally not `@Transactional`. Steps 3 and 5 each manage their own transaction. If step 5 fails after step 3 commits, the user record is updated but no tokens are issued; the client receives a 500 and can retry sign-in cleanly.

**`POST /api/v1/auth/google/verify`** — retained as a development-only debug route. Verifies a Google idToken and returns the extracted claims without touching the database or issuing any tokens.

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

### VARCHAR over PostgreSQL native enum types for JPA-mapped columns

The original V1 migration used `CREATE TYPE user_status AS ENUM (...)` and `CREATE TYPE auth_provider AS ENUM (...)` for the `status` and `provider` columns. This caused a hard runtime failure:

```
ERROR: operator does not exist: auth_provider = character varying
Hint: No operator matches the given name and argument types. You might need to add explicit type casts.
```

**Root cause:** Hibernate sends Java enum values as `character varying` (string) JDBC parameters because the entities use `@Enumerated(EnumType.STRING)`. PostgreSQL refuses to compare a native custom enum column against a `varchar` parameter without an explicit cast. PostgreSQL 18 is particularly strict about this.

**Fix applied:** Both columns were converted to `VARCHAR` in the live database using `ALTER COLUMN ... TYPE VARCHAR USING ...::text`, and the V1 migration SQL was rewritten to use `VARCHAR` from the start — no `CREATE TYPE` statements at all.

**Rule going forward:** Never use `CREATE TYPE ... AS ENUM` for columns that JPA entities map with `@Enumerated(EnumType.STRING)`. Use `VARCHAR` in the DDL. The application-level enum safety (restricting to known values) is enforced by the Java type system and the `@Enumerated` annotation, not by the database column type.

---

## What has not been implemented yet

- **⚠️ HIGHEST PRIORITY — Flyway migration auto-configuration** (see "Immediate next step" and "Problems encountered" below)
- Refresh token rotation service (validate an incoming refresh token, revoke it, issue a new pair)
- JWT authentication filter (validate `Authorization: Bearer` on every protected request)
- Protected route authorization (roles, ownership checks)
- Global API success response wrapper (align success and error response envelopes)
- Production-grade observability:
  - Structured JSON logging
  - Request-scoped correlation ID
  - Log masking for sensitive fields
  - Actuator metrics and health endpoints
  - Centralized log shipping

---

## Immediate next step

### ⚠️ HIGHEST PRIORITY — Fix Flyway migration auto-configuration

**Why this is the highest priority:** Flyway is currently disabled (`spring.flyway.enabled=false`). The database schema was created manually by running V1 and V2 SQL directly in pgAdmin. Without a working Flyway setup, every future schema change requires manual SQL execution against every database. This is fragile, error-prone, and completely blocks collaborative or multi-environment development.

**Current workaround state:**
- `spring.flyway.enabled=false` in `application.properties`
- Tables `users`, `user_auth_identities`, `refresh_tokens` were created manually in the `travelwiki` PostgreSQL database
- `flyway_schema_history` table does not exist

**What was tried:**
- `flyway-core` alone (Spring Boot 4.x BOM version) → Flyway produced zero log output; auto-configuration was not triggering
- Added `flyway-database-postgresql` (also BOM version) → still zero Flyway output
- Removed `baseline-on-migrate=true` → no change in behaviour
- Cleaned, reloaded Maven, full restart → still no Flyway output

**Most likely root causes to investigate:**
1. Spring Boot 4.x reorganised auto-configuration loading. The Flyway auto-configuration class (`FlywayAutoConfiguration`) may have moved or its `@ConditionalOn*` conditions may have changed compared to Spring Boot 3.x. Check the Spring Boot 4.0 migration guide for Flyway-specific changes.
2. PostgreSQL 18.1 may not be a recognised database version in whichever Flyway version Spring Boot 4.0.6 manages. Flyway may silently skip all migrations when it cannot confirm the database type.
3. The Spring Boot DevTools classloader hierarchy may be preventing Flyway's ServiceLoader-based database plugin registration from working. Try disabling DevTools as a diagnostic step.

**Recommended investigation steps:**
1. Check what Flyway version `mvn dependency:tree` reports for `flyway-core` in this project
2. Enable `DEBUG` logging for `org.flywaydb` in `application.properties` (`logging.level.org.flywaydb=DEBUG`) to see if Flyway produces any output at all
3. Check the Spring Boot 4.0 release notes for Flyway auto-configuration changes
4. If the BOM version is the issue, try pinning Flyway to a specific version known to support PostgreSQL 18 (e.g., 11.x latest)
5. As a last resort, configure Flyway programmatically via a `@Bean` rather than relying on auto-configuration

**Once Flyway is running, the database baseline must be set** because the tables already exist without a `flyway_schema_history`:
- Add `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=2` temporarily
- On first successful Flyway run, it will create `flyway_schema_history` and mark V1+V2 as the baseline
- Remove the baseline properties afterwards; future migrations (V3, V4, ...) will run normally

### JWT authentication filter (second priority, after Flyway)

The sign-in endpoint now issues tokens. Once Flyway is fixed, the next feature layer is a `JwtAuthenticationFilter` that intercepts every incoming request, reads the `Authorization: Bearer <token>` header, validates the JWT, and populates the Spring Security `SecurityContextHolder` with the authenticated user principal.

Recommended implementation direction:

- Create `JwtAuthenticationFilter extends OncePerRequestFilter`
- Extract the token from the `Authorization` header; skip the filter if the header is absent (let Spring Security reject unauthenticated requests for protected routes)
- Parse and verify the JWT using `Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token)`
- On success: build a `UsernamePasswordAuthenticationToken` with the `userId` as principal and no credentials; set it on `SecurityContextHolder`
- On failure (expired, malformed, wrong signature): clear the context and let the request continue — Spring Security will reject it at the authorization layer with 401
- Register the filter in `SecurityConfig` with `http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`
- Add `JwtService.extractUserId(String token) → Long` or a `validateAndParse` method to the interface so the filter can call it without duplicating jjwt logic

---

## Suggested next implementation order

1. ~~Auth service — find or create user~~ ✅ Done
2. ~~JWT generation service~~ ✅ Done
3. ~~Refresh token service (issue)~~ ✅ Done
4. ~~Full sign-in controller endpoint — `POST /api/v1/auth/google/signin`~~ ✅ Done
5. **⚠️ Fix Flyway migration auto-configuration** ← HIGHEST PRIORITY
6. JWT authentication filter
7. Protected endpoint smoke test
8. Refresh token rotation endpoint — `POST /api/v1/auth/google/refresh`
9. Global API success response wrapper (align success and error envelopes)
10. Request logging with correlation ID
11. Actuator and metrics when deployment monitoring becomes relevant

---

## Auth flow — what is done vs what is pending

### What the server can do right now (end-to-end implemented)

1. Android sends `POST /api/v1/auth/google/signin` with a Google `idToken`
2. Backend validates `GoogleAuthRequest`
3. Backend verifies the Google token cryptographically → `VerifiedGoogleToken`
4. Backend finds or creates the local `User` → `AuthUserResult`
5. Backend signs a short-lived JWT access token → `JwtToken`
6. Backend generates, SHA-256 hashes, and persists a refresh token → `RefreshTokenResult`
7. Backend returns `AuthResponse { user, tokens { accessToken, refreshToken, ... }, newUser }`

### What is still pending

8. Android sends `Authorization: Bearer <accessToken>` on protected endpoints → **JWT filter not yet wired**
9. Android sends `POST /api/v1/auth/google/refresh` with an expired access token + refresh token → **rotation endpoint not yet implemented**

---

## Problems encountered (kept for future context — delete once resolved)

### P1 — Flyway auto-configuration not triggering in Spring Boot 4.x

**Observed symptom:** Zero Flyway output in the startup log. No `Creating Schema History table` line, no `Migrating schema` lines, nothing. The application starts successfully but the database tables are never created.

**Environment:** Spring Boot 4.0.6, Hibernate 7.2.12, PostgreSQL 18.1, Java 21.

**What was tried and failed:**
- `flyway-core` alone (BOM version) → no output
- Adding `flyway-database-postgresql` (BOM version) → still no output
- Removing `baseline-on-migrate=true` → no change
- Maven clean + reload + full stop/start → no change

**Workaround applied:** `spring.flyway.enabled=false`. Tables created manually in pgAdmin from V1 + V2 SQL. `flyway_schema_history` does not exist in the database.

**Impact:** Every schema change requires manually running SQL in pgAdmin. Unacceptable beyond local development.

**Status:** Unresolved. Highest priority.

---

### P2 — PostgreSQL native enum types incompatible with Hibernate / JPA

**Observed symptom:**
```
ERROR: operator does not exist: auth_provider = character varying
Hint: No operator matches the given name and argument types. You might need to add explicit type casts.
```

**Root cause:** The original V1 SQL created `status` and `provider` columns using `CREATE TYPE ... AS ENUM (...)`. Hibernate 7.x sends Java enum values as plain string (`character varying`) JDBC parameters when `@Enumerated(EnumType.STRING)` is used. PostgreSQL 18 rejects a comparison between a custom enum column and a varchar parameter without an explicit cast.

**Fix applied:**
- Ran `ALTER TABLE users ALTER COLUMN status TYPE VARCHAR(40) USING status::text;`
- Ran `ALTER TABLE user_auth_identities ALTER COLUMN provider TYPE VARCHAR(50) USING provider::text;`
- Dropped `DROP TYPE user_status;` and `DROP TYPE auth_provider;`
- Rewrote `V1__create_auth_tables.sql` to use `VARCHAR` from the start — no `CREATE TYPE` statements

**Rule established:** Never use `CREATE TYPE ... AS ENUM` for columns mapped by `@Enumerated(EnumType.STRING)`. Use VARCHAR in the DDL.

**Status:** Resolved.

---

## Risks and open issues

### 1. Concurrent registration edge case (deferred)

If two sign-in requests for the exact same new Google user arrive in the same millisecond, the second INSERT will fail with a `DataIntegrityViolationException` from the unique constraint. This propagates as HTTP 500 until the REQUIRES_NEW retry pattern is implemented. The risk in a travel wiki app with real traffic is effectively zero.

### 2. CORS is permissive in development

`SecurityConfig` currently allows `*` origins. This must be locked to the Android app's origin (or removed for mobile-only APIs where CORS is irrelevant) before any production deployment.

### 3. Google audience configuration must be correct

`auth.google.allowed-audiences` must match the Google client IDs used by the Android app. A mismatch will reject every valid sign-in silently at the verification layer.

### 4. JWT secret must not be in source control

`auth.jwt.secret` is backed by `${JWT_SECRET}`. The `application.properties` fallback is a clearly labeled dev-only placeholder. The production value must come from an environment variable or secrets manager and must never be committed to version control.

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
| Auth database schema (V1) — VARCHAR columns (not native enum) | ✅ Done |
| Schema alignment fix (V2) | ✅ Done |
| JPA auth entities | ✅ Done |
| Auth repositories | ✅ Done |
| Flyway integration | ❌ Broken — auto-config not triggering; disabled as workaround |
| Google auth request/response DTOs | ✅ Done |
| `VerifiedGoogleToken` internal DTO | ✅ Done |
| `AuthUserResult` internal service result | ✅ Done |
| Google token verification service | ✅ Done |
| Auth service — find or create user | ✅ Done |
| `UserSuspendedException` + 403 handling | ✅ Done |
| `ApiErrorCode.USER_SUSPENDED` | ✅ Done |
| Centralized exception handler | ✅ Done |
| Spring Security route configuration | ✅ Done |
| Verification-only controller endpoint | ✅ Done (dev debug only) |
| SLF4J logging in exception handler and auth service | ✅ Done |
| `JwtProperties` configuration record | ✅ Done |
| `jjwt` dependency (0.12.6) | ✅ Done |
| JWT generation service (`JwtService` + `JwtServiceImpl`) | ✅ Done |
| `JwtToken` internal result record | ✅ Done |
| Refresh token issuance service (`RefreshTokenService` + impl) | ✅ Done |
| `RefreshTokenResult` internal result record | ✅ Done |
| Full sign-in endpoint — `POST /api/v1/auth/google/signin` | ✅ Done (end-to-end tested) |
| **Fix Flyway auto-configuration** | ⚠️ Highest priority |
| JWT authentication filter | ⏳ Pending |
| Protected endpoint testing | ⏳ Pending |
| Refresh token rotation endpoint | ⏳ Pending |
| Global API success response wrapper | ⏳ Pending |
| Structured logging + correlation ID | ⏳ Pending |
| Actuator + metrics | ⏳ Pending |
