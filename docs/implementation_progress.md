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

- Phase 0 — Curated-Only RAG Pipeline (full plan below)
- Production-grade observability (deferred, not blocking business features):
  - Structured JSON logging
  - Request-scoped correlation ID
  - Log masking for sensitive fields
  - Actuator metrics and health endpoints
  - Centralized log shipping

---

## Phase 0: Curated-Only RAG Pipeline — Implementation Plan

This phase delivers an end-to-end trip generation pipeline using only Tier 1 (curated structured DB) as the knowledge source. It is the MVP described in `travelwiki_rag_architecture.md` Section 11, Phase 0. All RAG ports are defined here so later phases (Tier 2 vector search, Tier 3 external APIs) plug in without touching the orchestrator.

The pipeline, in execution order:

```
POST /api/v1/trips/generate
  → TripRequestValidator
  → ItineraryCache.get(key)            ← return cached plan on hit
  → TripDuration calculation
  → DestinationService.resolve()       ← Tier 1 alias lookup
  → RetrievalOrchestrator.retrieve()   ← Phase 0: StructuredRetriever only
  → ContextAssembler.assemble()        ← Tier 1 chunks → GroundedContext
  → ItineraryPromptBuilder.build()     ← GroundedContext → prompt string
  → AiItineraryService.generate()      ← OpenAI call, retry once on failure
  → AiItineraryValidator.validate()    ← structural check
  → GroundingValidator.validate()      ← heuristic: key entities in output
  → BudgetEstimationService.estimate() ← deterministic Tier 1 only
  → TripPersistenceService.save()      ← single @Transactional
  → ItineraryCache.put(key, result)
  → TripQueryService.getTripDetails()  ← ownership-enforced load
  → ApiResponse<TripDetailResponse>
```

---

### Stage 1 — Common Infrastructure

**Goal:** lay the shared plumbing all business features depend on.

**`common/response/ApiResponse.java`**
```java
public record ApiResponse<T>(boolean success, String message, T data) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
}
```
All trip endpoints return `ResponseEntity<ApiResponse<T>>`. Auth endpoints are left as-is.

**New exception classes** (`common/exception/`):
- `BadRequestException` → 400 (generic validation failures from service layer)
- `NotFoundException` → 404
- `ForbiddenException` → 403 (ownership violation — never reveals whether the resource exists)
- `DestinationNotSupportedException` → 400, code `DESTINATION_NOT_SUPPORTED`
- `AiProviderException` → 502, code `AI_PROVIDER_ERROR`
- `AiResponseValidationException` → 500, code `AI_RESPONSE_INVALID`

**New `ApiErrorCode` values** (add to existing enum):
```
DESTINATION_NOT_SUPPORTED
TRIP_NOT_FOUND
AI_PROVIDER_ERROR
AI_RESPONSE_INVALID
```

**`GlobalExceptionHandler` additions** — new `@ExceptionHandler` methods for each new exception class, following the existing pattern (safe message to client, detail logged at WARN/ERROR).

**Shared enums** (`common/enums/` or in their feature package):
- `BudgetType` — `BUDGET`, `MID_RANGE`, `LUXURY`
- `TripStatus` — `DRAFT`, `GENERATING`, `GENERATED`, `FAILED`, `COMPLETED`
- `ItineraryItemCategory` — `TRANSPORT`, `FOOD`, `ACCOMMODATION`, `ACTIVITY`, `REST`, `SHOPPING`, `BUFFER`, `OTHER`

---

### Stage 2 — Destination Domain (Tier 1 Structured Data)

**Goal:** make `DestinationService.resolveDestination()` and `loadContext()` fully operational, backed by DB seed data for Saint Martin.

**Entities** (`destination/entity/`):
- `Destination` — maps `destinations` table
- `DestinationActivity` — maps `destination_activities` table; `@ManyToOne` to `Destination`
- `DestinationAlias` — maps `destination_aliases` table; `@ManyToOne` to `Destination`

**Repositories** (`destination/repository/`):
- `DestinationRepository`
- `DestinationActivityRepository` — `findByDestinationId(Long destinationId)`
- `DestinationAliasRepository` — `findByAliasIgnoreCase(String alias)`

**`DestinationContext`** record (`destination/dto/`):
```java
public record DestinationContext(Destination destination, List<DestinationActivity> activities) {}
```

**`DestinationService`** (`destination/service/`):
- `resolveDestination(String userInput) → Destination` — lowercase the input, query aliases; throw `DestinationNotSupportedException` if no match
- `loadContext(Long destinationId) → DestinationContext`

**Flyway migrations:**
- `V3__create_destination_tables.sql` — creates `destinations`, `destination_activities`, `destination_aliases`
- `V6__seed_destinations.sql` — inserts Saint Martin destination row, all alias variants (`saint martin`, `st martin`, `saint martin island`, `st. martin`), and all known activities with estimated costs and durations

---

### Stage 3 — Transport and Cost Rules (Tier 1 Structured Data)

**Goal:** make transport template lookup and deterministic budget estimation fully operational.

**Entities** (`transport/entity/`, `budget/` has no entity):
- `TransportTemplate` — maps `transport_templates`; `@ManyToOne` to `Destination`
- `CostRule` — maps `cost_rules`; `@ManyToOne` to `Destination`

**Repositories** (`transport/repository/`):
- `TransportTemplateRepository` — `findByFromLocationIgnoreCaseAndDestinationIdAndBudgetType(String, Long, BudgetType)` plus fallback query for `MID_RANGE`
- `CostRuleRepository` — `findByDestinationIdAndBudgetType(Long, BudgetType)`

**`TripDuration`** record (`trip/dto/` or `common/`):
```java
public record TripDuration(long tripDays, long tripNights) {
    public static TripDuration from(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return new TripDuration(days, Math.max(days - 1, 0));
    }
}
```

**`TransportService`** (`transport/service/`):
- `findBestTemplate(String startLocation, Long destinationId, BudgetType budgetType) → TransportTemplate` — exact match first, then fall back to `MID_RANGE`

**`BudgetEstimate`** record (`budget/dto/`): `transportCost`, `foodCost`, `accommodationCost`, `activityCost`, `miscCost`, `bufferCost`, `totalCost`, `perPersonCost`, `currency`.

**`BudgetEstimationService`** (`budget/service/`):
- `estimate(GenerateTripRequest, Destination, TransportTemplate, TripDuration) → BudgetEstimate`
- Applies the formulas from the guideline exactly: `rooms = ceil(travelers / 2.0)`, midpoint arithmetic, 10% buffer. Pure in-memory; zero AI coupling. This is the trust anchor.

**Flyway migrations:**
- `V4__create_transport_and_cost_tables.sql` — creates `transport_templates`, `cost_rules`
- `V7__seed_transport_templates.sql` — inserts Dhaka → Saint Martin rows for all three budget types
- `V8__seed_cost_rules.sql` — inserts cost rules for Saint Martin for all three budget types

---

### Stage 4 — Trip and Itinerary Persistence Layer

**Goal:** all entities and services needed to save a complete generated trip in one transaction, and to retrieve it with ownership enforcement.

**Entities** (`trip/entity/`):
- `Trip` — maps `trips`; owns the top-level trip row; `status` stored as `VARCHAR`
- `ItineraryDay` — maps `itinerary_days`; `@ManyToOne` to `Trip`
- `ItineraryItem` — maps `itinerary_items`; `@ManyToOne` to `ItineraryDay`; `category` stored as `VARCHAR`
- `TripBudget` — maps `trip_budgets`; `@OneToOne` to `Trip`
- `TripMealPlan` — maps `trip_meal_plans`; `@ManyToOne` to `Trip`
- `TripAccommodationSuggestion` — maps `trip_accommodation_suggestions`; `@OneToOne` to `Trip`

**Repositories** (`trip/repository/`): one per entity. Critical repository method: `TripRepository.findByIdAndUserId(Long tripId, Long userId)` — never load a trip by ID alone.

**Response DTOs** (`trip/dto/`):
- `TripDetailResponse` — the full itinerary response returned to Android
- `TripSummaryResponse` — lightweight list item
- `BudgetResponse`, `TransportPlanResponse`, `ItineraryDayResponse`, `ItineraryItemResponse`, `MealPlanResponse`, `AccommodationSuggestionResponse`

**`TripPersistenceService`** (`trip/service/`):
- `saveGeneratedTrip(userId, request, destination, duration, aiResponse, budget, transportTemplate) → Trip`
- Single `@Transactional` boundary. Saves `Trip` → `ItineraryDay` list → `ItineraryItem` list → `TripBudget` → `TripMealPlan` list → `TripAccommodationSuggestion` atomically. Nothing is written if any step fails.

**`TripQueryService`** (`trip/service/`):
- `getTripDetails(Long tripId, Long userId) → TripDetailResponse` — calls `findByIdAndUserId`; throws `ForbiddenException` (not `NotFoundException`) if the trip exists but belongs to another user, to prevent resource existence enumeration
- `listUserTrips(Long userId) → List<TripSummaryResponse>`
- `deleteTrip(Long tripId, Long userId)` — ownership check before delete

**Flyway migration:**
- `V5__create_trip_tables.sql` — creates `trips`, `itinerary_days`, `itinerary_items`, `trip_budgets`, `trip_meal_plans`, `trip_accommodation_suggestions`

---

### Stage 5 — RAG Retrieval Layer (Phase 0: Tier 1 Only)

**Goal:** define the full retrieval port hierarchy so Phase 1 can plug in `VectorRetriever` without touching the orchestrator. In Phase 0, the only active retriever is `StructuredRetriever`.

**DTOs** (`retrieval/dto/`):

`RetrievalQuery` — what all retrievers receive:
```java
public record RetrievalQuery(
    String destinationCode,
    String rawDestinationText,
    BudgetType budgetType,
    String startLocation,
    TripDuration duration,
    List<String> preferences
) {}
```

`RetrievedChunk` — what each retriever returns:
```java
public record RetrievedChunk(
    String sourceId,       // e.g. "dest:SAINT_MARTIN", "transport:42"
    int tier,              // 1, 2, or 3
    String content,        // formatted text for prompt injection
    double score,          // relevance score for RRF fusion
    Map<String, Object> metadata  // structured facts (costs, durations, etc.)
) {}
```

`GroundedContext` — assembled context handed to the prompt builder:
```java
public record GroundedContext(
    List<RetrievedChunk> chunks,    // ordered by tier desc, then score desc
    DestinationContext destinationContext,  // Tier 1 structured facts, for budget engine
    TransportTemplate transportTemplate,   // Tier 1, for budget engine
    int tokenEstimate
) {}
```

**Port** (`retrieval/KnowledgeRetriever.java`):
```java
public interface KnowledgeRetriever {
    List<RetrievedChunk> retrieve(RetrievalQuery query);
    int tier();  // identifies which tier this retriever serves
}
```

**`StructuredRetriever`** (`retrieval/adapter/`):
- Implements `KnowledgeRetriever`, `tier() = 1`
- Uses `DestinationService` and `TransportService` to load Tier 1 data
- Formats each piece (destination description, activities list, transport route, cost rules) into a `RetrievedChunk` with `tier=1` and a high base score
- This is the only active retriever in Phase 0

**`Reranker` port** (`retrieval/Reranker.java`):
```java
public interface Reranker {
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> chunks, int topK);
}
```

**`NoOpReranker`** (`retrieval/adapter/`): returns the input list up to `topK` unchanged. The active adapter for Phase 0 and 1.

**`RetrievalOrchestrator`** (`retrieval/`):
- Phase 0: calls only `StructuredRetriever`, skips RRF fusion (single source), calls `NoOpReranker`
- Designed to accept a `List<KnowledgeRetriever>` so Phase 1 adds `VectorRetriever` to the list without any other change

**`ContextAssembler`** (`retrieval/`):
- Takes the ranked `List<RetrievedChunk>` from the orchestrator
- Orders chunks by tier (ascending = highest trust first in prompt), then by score
- Applies a token budget cap: drops lowest-tier chunks when over budget
- Phase 0: all chunks are tier 1, so no dropping occurs
- Returns `GroundedContext` with the assembled chunk list plus the raw Tier 1 objects needed by the budget engine

---

### Stage 6 — AI Integration

**Goal:** `AiItineraryService.generate(prompt)` returns a parsed, typed `AiItineraryResponse`.

**`AiProperties`** (`ai/config/`):
- `@ConfigurationProperties(prefix = "ai")`
- Fields: `model`, `apiKey`, `timeoutSeconds`, `maxRetries`

**`AiClient`** (`ai/client/`):
- Spring `RestClient` with 60s read timeout
- `callChatCompletions(String prompt, double temperature) → String` — returns the raw `content` string from OpenAI's response
- Reads `ai.api-key` from env; never logs it
- Throws `AiProviderException` on HTTP error, timeout, or missing content

**AI response DTOs** (`ai/dto/`): `AiItineraryResponse`, `AiDayResponse`, `AiItemResponse`, `AiTransportPlanResponse`, `AiMealPlanResponse`, `AiAccommodationResponse` — Java records mirroring the JSON schema defined in the guideline exactly.

**`ItineraryPromptBuilder`** (`itinerary/prompt/`):
- Input: `GroundedContext` + `GenerateTripRequest` + `TripDuration`
- Iterates over `GroundedContext.chunks()` to build the `=== DESTINATION CONTEXT ===` and `=== TRANSPORT CONTEXT ===` sections
- Embeds the full required JSON schema inline (from guideline Step 9)
- In Phase 0, the chunks are all Tier 1 so the output is equivalent to the original hardcoded prompt; the key difference is the prompt is now driven by retrieved context rather than direct entity access

**`AiItineraryService`** (`itinerary/ai/`):
- `generate(String prompt) → AiItineraryResponse`
- Calls `AiClient` at temperature `0.7` for the first attempt
- On `JsonProcessingException`: retries once at temperature `0.0` with a stricter schema hint in the prompt
- On second failure: throws `AiResponseValidationException`
- On `AiProviderException`: re-throws immediately (no retry for provider failures)

---

### Stage 7 — Validation

**Goal:** two gates that an AI response must pass before any data is written to the database.

**`AiItineraryValidator`** (`itinerary/validator/`):
Structural checks (throws `AiResponseValidationException` on failure):
- Response is not null
- `title` is not blank
- `days` list is not null and not empty
- `days.size() == expectedTripDays`
- Every day has a non-empty `items` list

**`GroundingValidator`** (`itinerary/validator/`):
Phase 0 heuristic grounding check:
- Extracts key entities from `GroundedContext` (destination name, major location names from activities, route keywords from transport template)
- Checks that at least N of those entities appear somewhere in the serialized AI response (case-insensitive)
- Returns a `GroundingResult(double score, boolean passed, String reason)`
- Phase 0 threshold is lenient (e.g. 0.4 — curated destinations always produce grounded output); if below threshold, attempt one regeneration; if still below, attach a `LOW_CONFIDENCE` flag rather than failing (since the destination is curated, a partial grounding score is a minor concern)

---

### Stage 8 — Itinerary Cache

**Goal:** identical requests return the cached plan without calling the LLM, making the response fully idempotent.

**`CacheKey`** record (`common/cache/`):
```java
public record CacheKey(
    String destinationCode,
    LocalDate startDate,
    LocalDate endDate,
    int travelerCount,
    BudgetType budgetType,
    String preferencesHash  // SHA-256 of sorted preferences list, hex-encoded
) {}
```

**`ItineraryCache` port** (`common/cache/`):
```java
public interface ItineraryCache {
    Optional<TripDetailResponse> get(CacheKey key);
    void put(CacheKey key, TripDetailResponse response);
    void evict(CacheKey key);
}
```

**`InMemoryItineraryCache`** (`common/cache/`):
- `ConcurrentHashMap<CacheKey, TripDetailResponse>` — simple Phase 0 implementation
- No TTL, no size limit (acceptable for MVP; replaced by Redis in Phase 3)
- The port is defined here so Phase 3 swaps the adapter with zero change to the orchestrator

---

### Stage 9 — Orchestration: Wire Everything Together

**Goal:** `TripGenerationService` sequences Stages 1–8 end-to-end; `TripController` exposes the four trip endpoints.

**`TripRequestValidator`** (`trip/`):
- `validate(GenerateTripRequest)` — throws `BadRequestException` for:
  - `endDate` before `startDate`
  - trip duration < 1 day
  - trip duration > 30 days

**`TripGenerationService`** (`trip/service/`) — thin orchestrator, no `@Transactional` at this level:
```
1. tripRequestValidator.validate(request)
2. cacheKey = buildCacheKey(request)
3. cache.get(cacheKey) → return if present
4. duration = TripDuration.from(request.startDate(), request.endDate())
5. destination = destinationService.resolveDestination(request.destination())
6. query = RetrievalQuery.from(destination, request, duration)
7. chunks = retrievalOrchestrator.retrieve(query)
8. groundedContext = contextAssembler.assemble(chunks, destination, transportTemplate)
9. prompt = itineraryPromptBuilder.build(groundedContext, request, duration)
10. aiResponse = aiItineraryService.generate(prompt)
11. aiItineraryValidator.validate(aiResponse, duration.tripDays())
12. groundingResult = groundingValidator.validate(aiResponse, groundedContext)
13. budget = budgetEstimationService.estimate(request, destination, groundedContext.transportTemplate(), duration)
14. trip = tripPersistenceService.saveGeneratedTrip(userId, request, destination, duration, aiResponse, budget, groundedContext.transportTemplate())
15. result = tripQueryService.getTripDetails(trip.getId(), userId)
16. cache.put(cacheKey, result)
17. return result
```

**`TripController`** (`trip/controller/`):
```
POST  /api/v1/trips/generate   → generateTrip()  → 200 ApiResponse<TripDetailResponse>
GET   /api/v1/trips             → listTrips()     → 200 ApiResponse<List<TripSummaryResponse>>
GET   /api/v1/trips/{tripId}    → getTrip()       → 200 ApiResponse<TripDetailResponse>
DELETE /api/v1/trips/{tripId}   → deleteTrip()    → 204 No Content
```

`userId` is always resolved from `SecurityContextHolder` — never accepted as a request parameter.

---

### Phase 0 package layout

```
com.example.travelwiki
├── trip/
│   ├── controller/TripController.java
│   ├── dto/  (GenerateTripRequest, TripDetailResponse, TripSummaryResponse,
│   │          BudgetResponse, TransportPlanResponse, ItineraryDayResponse,
│   │          ItineraryItemResponse, MealPlanResponse, AccommodationSuggestionResponse,
│   │          TripDuration)
│   ├── entity/ (Trip, ItineraryDay, ItineraryItem, TripBudget, TripMealPlan,
│   │            TripAccommodationSuggestion)
│   ├── repository/ (TripRepository, ItineraryDayRepository, ItineraryItemRepository,
│   │               TripBudgetRepository, TripMealPlanRepository,
│   │               TripAccommodationSuggestionRepository)
│   └── service/ (TripGenerationService, TripPersistenceService, TripQueryService,
│                 TripRequestValidator)
│
├── destination/
│   ├── dto/DestinationContext.java
│   ├── entity/ (Destination, DestinationActivity, DestinationAlias)
│   ├── repository/ (DestinationRepository, DestinationActivityRepository,
│   │               DestinationAliasRepository)
│   └── service/DestinationService.java
│
├── transport/
│   ├── entity/TransportTemplate.java
│   ├── repository/TransportTemplateRepository.java
│   └── service/TransportService.java
│
├── budget/
│   ├── dto/BudgetEstimate.java
│   ├── entity/CostRule.java
│   ├── repository/CostRuleRepository.java
│   └── service/BudgetEstimationService.java
│
├── retrieval/
│   ├── KnowledgeRetriever.java         ← port interface
│   ├── Reranker.java                   ← port interface
│   ├── RetrievalOrchestrator.java
│   ├── ContextAssembler.java
│   ├── dto/ (RetrievalQuery, RetrievedChunk, GroundedContext)
│   └── adapter/
│       ├── StructuredRetriever.java    ← Phase 0 only active retriever
│       └── NoOpReranker.java
│
├── itinerary/
│   ├── ai/AiItineraryService.java
│   ├── prompt/ItineraryPromptBuilder.java
│   └── validator/ (AiItineraryValidator, GroundingValidator, GroundingResult)
│
├── ai/
│   ├── client/AiClient.java
│   ├── config/AiProperties.java
│   ├── dto/ (AiItineraryResponse, AiDayResponse, AiItemResponse,
│   │         AiTransportPlanResponse, AiMealPlanResponse, AiAccommodationResponse)
│   └── exception/ (AiProviderException, AiResponseValidationException)
│
└── common/
    ├── cache/ (ItineraryCache interface, InMemoryItineraryCache, CacheKey)
    ├── enums/ (BudgetType, TripStatus, ItineraryItemCategory)
    ├── exception/ (BadRequestException, NotFoundException, ForbiddenException,
    │              DestinationNotSupportedException — existing + new classes)
    └── response/ApiResponse.java
```

---

### Flyway migration sequence for Phase 0

Migrations are numbered strictly sequentially so they can be applied incrementally per stage without Flyway out-of-order errors.

| File | Stage | Creates |
|---|---|---|
| `V3__create_destination_tables.sql` | Stage 2 | `destinations`, `destination_activities`, `destination_aliases` |
| `V4__seed_destinations.sql` | Stage 2 | Saint Martin row + all alias variants + activities |
| `V5__create_transport_and_cost_tables.sql` | Stage 3 | `transport_templates`, `cost_rules` |
| `V6__seed_transport_templates.sql` | Stage 3 | Dhaka → Saint Martin for BUDGET, MID_RANGE, LUXURY |
| `V7__seed_cost_rules.sql` | Stage 3 | Cost rules for Saint Martin for BUDGET, MID_RANGE, LUXURY |
| `V8__create_trip_tables.sql` | Stage 4 | `trips`, `itinerary_days`, `itinerary_items`, `trip_budgets`, `trip_meal_plans`, `trip_accommodation_suggestions` |

All DDL uses `VARCHAR` for enum-backed columns. No `CREATE TYPE ... AS ENUM`.

---

### AI configuration addition (application.properties)

```properties
ai.provider=openai
ai.api-key=${OPENAI_API_KEY}
ai.model=gpt-4o-mini
ai.timeout-seconds=60
ai.max-retries=1
```

---

### Phase 0 success condition

A logged-in user can `POST /api/v1/trips/generate` with a Saint Martin request, receive a `TripDetailResponse` with a backend-calculated budget breakdown, and later reopen the saved trip via `GET /api/v1/trips/{tripId}`. A second identical request returns the cached plan without a new LLM call.

---

## Suggested next implementation order

1. ~~Auth service — find or create user~~ ✅ Done
2. ~~JWT generation service~~ ✅ Done
3. ~~Refresh token service (issue)~~ ✅ Done
4. ~~Full sign-in controller endpoint~~ ✅ Done
5. ~~Fix Flyway migration auto-configuration~~ ✅ Done
6. ~~JWT authentication filter~~ ✅ Done
7. ~~Refresh token rotation endpoint~~ ✅ Done
8. **Stage 1 — Common infrastructure** ← START HERE
9. Stage 2 — Destination domain + Flyway V3, V6
10. Stage 3 — Transport and cost rules + Flyway V4, V7, V8
11. Stage 4 — Trip persistence layer + Flyway V5
12. Stage 5 — RAG retrieval layer (Tier 1 only)
13. Stage 6 — AI integration
14. Stage 7 — Validation (structural + grounding)
15. Stage 8 — Itinerary cache
16. Stage 9 — Orchestration: TripGenerationService + TripController
17. End-to-end test: full Saint Martin generate flow
18. Request logging with correlation ID (deferred, non-blocking)
19. Actuator + metrics (deferred, non-blocking)

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
| **Phase 0 — Stage 1: Common infrastructure** | ✅ Done |
| Phase 0 — Stage 2: Destination domain + V3/V4 migrations | ✅ Done |
| Phase 0 — Stage 3: Transport + cost rules + V4/V7/V8 migrations | ⏳ Pending |
| Phase 0 — Stage 4: Trip persistence layer + V5 migration | ⏳ Pending |
| Phase 0 — Stage 5: RAG retrieval layer (Tier 1 only) | ⏳ Pending |
| Phase 0 — Stage 6: AI integration | ⏳ Pending |
| Phase 0 — Stage 7: Validation (structural + grounding) | ⏳ Pending |
| Phase 0 — Stage 8: Itinerary cache | ⏳ Pending |
| Phase 0 — Stage 9: Orchestration (TripGenerationService + TripController) | ⏳ Pending |
| End-to-end test: Saint Martin generate flow | ⏳ Pending |
| Structured logging + correlation ID | ⏳ Deferred |
| Actuator + metrics | ⏳ Deferred |
