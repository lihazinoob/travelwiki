# Implementation Progress

This file tracks what has been implemented in the backend so far and what remains next.

It should be read together with:

- `docs/task1_backend_spring_boot_guideline.md`

## Current project state

The project started as a bare Spring Boot backbone with:

- application entrypoint
- PostgreSQL configuration
- Spring Data JPA dependency

The project now has the initial auth data foundation prepared for production-oriented authentication work.

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

Updated `application.properties` to:

- switch from `spring.jpa.hibernate.ddl-auto=update` to `validate`
- enable Flyway
- move datasource values to environment-backed configuration

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

## What has not been implemented yet

The following auth features are still pending:

- register/authenticate API endpoint
- Google ID token verification
- backend JWT generation
- refresh token issuance flow
- refresh token rotation flow
- security configuration
- authentication filter / JWT filter
- protected route authorization
- auth response DTOs
- global API response wrapper
- global exception handler

## Immediate next step

The next implementation task should be:

### Google authentication API

Recommended direction:

- use a Google-based authentication endpoint instead of classic email/password registration
- Android sends Google `idToken`
- backend verifies the token
- backend finds or creates user
- backend issues backend JWT
- backend stores refresh token

Recommended endpoint direction:

- `POST /api/v1/auth/google`

This endpoint should support both:

- first-time registration
- returning-user login

## Suggested next implementation order

1. Create auth request/response DTOs
2. Create Google token verification service
3. Create auth service that finds or creates user
4. Create JWT service
5. Create refresh token service
6. Create auth controller endpoint
7. Add Spring Security configuration
8. Add protected endpoint testing

## Risks / notes

### 1. Existing database state

If the local database already contains tables created using `ddl-auto=update`, Flyway migration and schema validation may conflict with that existing schema.

This should be checked before running the application against a non-clean database.

### 2. Build verification not completed

Full Maven verification was not completed in this environment because:

- network access for dependency resolution is restricted here
- the generated Maven wrapper script appears broken in this project environment

So the structure has been prepared carefully, but runtime verification still needs to be done on the local machine with working Maven access.

## Progress summary

Completed now:

- auth database design finalized
- auth schema migration created
- auth entities created
- auth repositories created
- Flyway introduced
- unsafe hardcoded datasource password removed

Current position relative to the original roadmap:

- Stage 1: partially prepared
- Stage 2 Auth foundation:
  - `User entity` completed
  - `Register API` not yet implemented
  - `Login API` not yet implemented
  - `JWT generation` not yet implemented
  - `JWT filter` not yet implemented
  - `Secure trip endpoints` not yet implemented
