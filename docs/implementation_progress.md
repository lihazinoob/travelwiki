# Implementation Progress

This file tracks what has been implemented in the backend so far, how it is implemented, and what remains next.

It should be read together with:

- `docs/task1_backend_spring_boot_guideline.md`

## Current project state

The project started as a bare Spring Boot backbone with:

- application entrypoint
- PostgreSQL configuration
- Spring Data JPA dependency

The project now has the initial authentication foundation implemented for a production-oriented Google sign-in flow.

The current auth direction is:

- Android app uses `Sign in with Google`
- Android obtains a Google `idToken`
- backend verifies the Google `idToken`
- backend finds or creates the local user
- backend issues its own access token and refresh token

At the moment, the system has reached the point where the database model, DTO boundary, and Google token verification layer are prepared. The orchestration layer that turns a verified Google identity into a signed-in backend user is still next.

## What has been implemented

### 1. Auth schema foundation

Added a Flyway migration:

- `src/main/resources/db/migration/V1__create_auth_tables.sql`

This migration creates:

- `users`
- `user_auth_identities`
- `refresh_tokens`

### 2. Production-oriented schema improvements applied

The schema was designed with the following production concerns in mind:

- `users.email` is `NOT NULL` and `UNIQUE`
- `users.status` is constrained to:
  - `ACTIVE`
  - `SUSPENDED`
  - `DELETED`
- `user_auth_identities` separates provider identity from the main app user
- `user_auth_identities` uses:
  - `UNIQUE(provider, provider_subject)`
  - `UNIQUE(user_id, provider)`
- `provider` is constrained to `GOOGLE` for now
- `refresh_tokens.token_hash` is `UNIQUE`
- `refresh_tokens.replaced_by_token_id` references `refresh_tokens(id)`
- auth-related timestamps use `TIMESTAMPTZ`

### 3. JPA auth domain model

Added entities under:

- `src/main/java/com/example/travelwiki/auth/entity`

Current auth entities:

- `User`
- `UserAuthIdentity`
- `RefreshToken`
- `AuthProvider`
- `UserStatus`

### 4. Repository layer

Added repositories under:

- `src/main/java/com/example/travelwiki/auth/repository`

Current repositories:

- `UserRepository`
- `UserAuthIdentityRepository`
- `RefreshTokenRepository`

### 5. Dependency and configuration updates

Updated `pom.xml` to include:

- Spring Validation
- Spring Security
- Flyway
- Google API client library for token verification

Updated `application.properties` to:

- switch from `spring.jpa.hibernate.ddl-auto=update` to `validate`
- enable Flyway
- move datasource values to environment-backed configuration
- add Google allowed audience configuration:
  - `auth.google.allowed-audiences=${GOOGLE_ALLOWED_AUDIENCES:}`

Updated application bootstrapping:

- enabled `@ConfigurationPropertiesScan` in `TravelwikiApplication`

### 6. Auth request and response DTOs

Added DTOs under:

- `src/main/java/com/example/travelwiki/auth/dto`

Current auth DTOs:

- `GoogleAuthRequest`
- `AuthResponse`
- `AuthUserResponse`
- `AuthTokenPairResponse`
- `VerifiedGoogleToken`

What each DTO does:

- `GoogleAuthRequest`
  - request body for `POST /api/v1/auth/google`
  - currently contains the Google `idToken`
- `VerifiedGoogleToken`
  - internal verified-claims DTO returned by the Google token verification service
  - contains:
    - Google `sub`
    - email
    - email verified flag
    - display name
    - picture URL
- `AuthUserResponse`
  - backend user information returned to the client after successful sign-in
- `AuthTokenPairResponse`
  - backend-issued access token and refresh token response shape
- `AuthResponse`
  - final auth response wrapper containing user info, tokens, and `newUser`

### 7. Google token verification service

Added the Google token verification layer under:

- `src/main/java/com/example/travelwiki/auth/service`
- `src/main/java/com/example/travelwiki/auth/config`
- `src/main/java/com/example/travelwiki/auth/exception`

Added files:

- `GoogleTokenVerificationService`
- `GoogleTokenVerificationServiceImpl`
- `GoogleAuthProperties`
- `InvalidGoogleTokenException`

## How the Google verification layer is implemented

The backend now has a dedicated service responsible only for turning a frontend-provided Google token into trusted backend identity data.

### Service contract

The service contract is:

- `GoogleTokenVerificationService.verify(String idToken)`

It returns:

- `VerifiedGoogleToken`

### Configuration

The verifier reads allowed Google audiences from:

- `auth.google.allowed-audiences`

This is intended to be backed by:

- `GOOGLE_ALLOWED_AUDIENCES`

This value should contain the Google client ID or IDs accepted by the backend.

### Verification logic

`GoogleTokenVerificationServiceImpl` currently:

- rejects blank tokens
- verifies the token using Google’s Java client verifier
- enforces allowed audiences
- enforces Google issuers:
  - `accounts.google.com`
  - `https://accounts.google.com`
- rejects invalid or unverifiable tokens
- extracts token payload claims
- requires:
  - non-empty `sub`
  - non-empty `email`
  - `email_verified = true`
- maps the verified claims into `VerifiedGoogleToken`

### Why this is important

This is the trust boundary of the authentication system.

The frontend is allowed to send only a Google token string. The backend must verify it independently before:

- finding the user
- creating the user
- issuing JWTs
- storing refresh tokens

Without this layer, the backend would be trusting unverified frontend identity input.

## Important design decisions taken

### Why `user_auth_identities` exists

Authentication provider data should not be stored directly inside `users`.

`users` represents the app-level user.

`user_auth_identities` represents external identity providers such as Google.

This makes the design safer for:

- future Apple login
- future Facebook login
- account linking
- cleaner production auth modeling

### Why Google `sub` should be the identity key

For Google authentication, the stable external identifier is the Google token `sub` claim.

Email should not be treated as the provider identity key because email can change.

The intended mapping is:

- `provider = GOOGLE`
- `provider_subject = Google sub`

### Why refresh tokens are stored in a table

This allows:

- token revocation
- refresh token rotation
- logout support
- session tracking expansion later

### Why Google token verification is isolated in its own service

Google verification logic should not be mixed with:

- controller request handling
- user creation logic
- JWT generation
- refresh token persistence

This keeps the trust boundary explicit and keeps the auth flow easier to test and extend.

## What has not been implemented yet

The following auth features are still pending:

- auth service that finds or creates the user after Google verification
- backend JWT generation
- refresh token issuance flow
- refresh token rotation flow
- auth controller endpoint
- security configuration
- authentication filter / JWT filter
- protected route authorization
- global API response wrapper
- global exception handler

## Immediate next step

The next implementation task should be:

### Auth service that finds or creates the user

Recommended direction:

- accept `VerifiedGoogleToken` from the verification service
- look up `user_auth_identities` using:
  - `provider = GOOGLE`
  - `provider_subject = verifiedGoogleToken.subject()`
- if found:
  - load the linked `User`
  - update `lastLoginAt`
  - optionally refresh `emailAtAuthTime`
  - optionally sync profile fields like display name and picture URL
- if not found:
  - create `User`
  - set email from verified token
  - set `emailVerified = true`
  - set initial display name and picture URL from verified token
  - create `UserAuthIdentity`
- return a clean internal result that the JWT/refresh token services can use next

## Suggested next implementation order

1. Create auth service that finds or creates user from `VerifiedGoogleToken`
2. Create JWT service
3. Create refresh token service
4. Create auth controller endpoint for `POST /api/v1/auth/google`
5. Add global API response wrapper
6. Add global exception handler
7. Add Spring Security configuration
8. Add authentication filter / JWT filter
9. Add protected endpoint testing

## Recommended next auth flow after current state

The intended flow should now be:

1. Android app gets Google `idToken`
2. Android sends `POST /api/v1/auth/google`
3. Backend validates `GoogleAuthRequest`
4. Backend verifies the Google token
5. Backend finds or creates local user
6. Backend issues backend access token
7. Backend stores hashed refresh token
8. Backend returns `AuthResponse`
9. Android uses backend access token for protected endpoints

## Risks / notes

### 1. Existing database state

If the local database already contains tables created using `ddl-auto=update`, Flyway migration and schema validation may conflict with that existing schema.

This should be checked before running the application against a non-clean database.

### 2. Build verification not completed

Full Maven verification was not completed in this environment because:

- network access for dependency resolution is restricted here
- dependency download for the new Google library may require normal local Maven access
- the generated Maven wrapper script appears broken in this project environment

So the structure has been prepared carefully, but runtime verification still needs to be done on the local machine with working Maven access.

### 3. Audience configuration must be set correctly

The Google token verifier depends on `GOOGLE_ALLOWED_AUDIENCES`.

If this is not configured correctly, valid Google sign-ins will fail.

This should be aligned with the client IDs used by the Android app.

### 4. Exception handling is not wired yet

`InvalidGoogleTokenException` exists, but there is not yet a global exception handler mapping it into a standardized API response.

So the error contract is not finalized yet.

## Progress summary

Completed now:

- auth database design finalized
- auth schema migration created
- auth entities created
- auth repositories created
- Flyway introduced
- unsafe hardcoded datasource password removed
- Google auth request/response DTO boundary created
- Google token verification service implemented
- Google verifier configuration introduced

Current position relative to the original roadmap:

- Stage 1: partially prepared
- Stage 2 Auth foundation:
  - `User entity` completed
  - `Auth request/response DTOs` completed
  - `Google token verification service` completed
  - `Register API` not yet implemented
  - `Login API` not yet implemented
  - `JWT generation` not yet implemented
  - `JWT filter` not yet implemented
  - `Secure trip endpoints` not yet implemented
