# TravelWiki API Reference

This document is the authoritative reference for all backend API endpoints. It is written for the Android client team. Every endpoint, request shape, response shape, and error code is documented here.

**Base URL (local development):** `http://localhost:8080`

**All request and response bodies are JSON.**  
**All timestamps are ISO 8601 with UTC offset, e.g. `2026-05-30T10:15:00Z`.**

---

## Table of contents

1. [Authentication overview](#authentication-overview)
2. [Common response envelopes](#common-response-envelopes)
3. [Auth endpoints](#auth-endpoints)
   - [POST /api/v1/auth/google/signin](#post-apiv1authgooglesignin)
   - [POST /api/v1/auth/google/refresh](#post-apiv1authgooglerefresh)
4. [Using the access token on protected endpoints](#using-the-access-token-on-protected-endpoints)
5. [Error reference](#error-reference)
6. [Development-only endpoints](#development-only-endpoints)

---

## Authentication overview

The backend uses a two-token system:

| Token | TTL | Where it goes | Purpose |
|---|---|---|---|
| **Access token** (JWT) | 15 minutes | `Authorization: Bearer <token>` header on every protected request | Proves identity to the backend |
| **Refresh token** (opaque) | 30 days | Request body of `/refresh` only | Gets a new access token when the old one expires |

**Typical client flow:**

```
1. App launches → call /signin with Google idToken
2. Store both access token and refresh token locally
3. For every API call → send access token in Authorization header
4. If a protected endpoint returns 401 → call /refresh with the refresh token
5. Store the NEW access token and NEW refresh token returned by /refresh (old ones are dead)
6. Retry the original request with the new access token
7. If /refresh also returns 401 (REFRESH_TOKEN_EXPIRED) → user must sign in again with Google
```

---

## Common response envelopes

### Success response

Success responses return HTTP `200 OK` with the documented body directly. There is no outer wrapper object yet — the body IS the data.

### Error response

All errors return the following JSON body:

```json
{
  "success": false,
  "code": "ERROR_CODE_STRING",
  "message": "Human-readable message safe to display",
  "path": "/api/v1/auth/google/signin",
  "timestamp": "2026-05-30T10:00:00Z"
}
```

| Field | Type | Description |
|---|---|---|
| `success` | boolean | Always `false` for errors |
| `code` | string | Machine-readable error code — see [Error reference](#error-reference) |
| `message` | string | Human-readable message; safe to show to the user |
| `path` | string | The request path that produced the error |
| `timestamp` | string | ISO 8601 timestamp of when the error occurred |

---

## Auth endpoints

### POST /api/v1/auth/google/signin

Signs in with a Google ID token. Verifies the token cryptographically, resolves or creates the local user account, and returns a JWT access token and a refresh token.

**Authentication required:** No — this is a public endpoint.

#### Request

```
POST /api/v1/auth/google/signin
Content-Type: application/json
```

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6Ii..."
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `idToken` | string | Yes | Max 4096 chars | Google ID token obtained from the Android Google Sign-In SDK |

#### Success response — 200 OK

```json
{
  "user": {
    "id": 42,
    "email": "user@example.com",
    "emailVerified": true,
    "displayName": "Jane Doe",
    "pictureUrl": "https://lh3.googleusercontent.com/...",
    "status": "ACTIVE"
  },
  "tokens": {
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresAt": "2026-05-30T10:15:00Z",
    "refreshToken": "dGhpcyBpcyBhIHJhbmRvbSB0b2tlbg",
    "refreshTokenExpiresAt": "2026-06-29T10:00:00Z"
  },
  "newUser": false
}
```

| Field | Type | Description |
|---|---|---|
| `user.id` | number | Internal user ID — use this as the stable identifier for this user in all future requests |
| `user.email` | string | Email address from Google |
| `user.emailVerified` | boolean | Always `true` for Google sign-in |
| `user.displayName` | string or null | Display name from Google |
| `user.pictureUrl` | string or null | Profile picture URL from Google |
| `user.status` | string | Account status — always `ACTIVE` on a successful sign-in |
| `tokens.tokenType` | string | Always `"Bearer"` |
| `tokens.accessToken` | string | JWT access token — send in `Authorization` header on every protected request |
| `tokens.accessTokenExpiresAt` | string | When the access token expires (15 min from issuance) |
| `tokens.refreshToken` | string | Opaque refresh token — store securely, send only to `/refresh` |
| `tokens.refreshTokenExpiresAt` | string | When the refresh token expires (30 days from issuance) |
| `newUser` | boolean | `true` if this is the user's first-ever sign-in; `false` for returning users |

#### Error responses

| HTTP status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `idToken` field is missing or blank |
| 400 | `MALFORMED_REQUEST` | Request body is not valid JSON |
| 401 | `INVALID_GOOGLE_TOKEN` | Google ID token is expired, forged, wrong audience, or otherwise invalid |
| 403 | `USER_SUSPENDED` | The Google account maps to a suspended or deleted local account |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error |

---

### POST /api/v1/auth/google/refresh

Exchanges a valid refresh token for a new JWT access token and a new refresh token. The submitted refresh token is immediately revoked — the client must store and use the new refresh token returned in the response.

**Authentication required:** No — this endpoint is intentionally public because the access token may be expired when this is called.

**Important:** Call this endpoint only when the access token has expired (i.e., you received a `401` from a protected endpoint). Calling it unnecessarily burns the refresh token and forces a new one into storage.

#### Request

```
POST /api/v1/auth/google/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJhbmRvbSB0b2tlbg"
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `refreshToken` | string | Yes | Max 512 chars | The refresh token previously received from `/signin` or a prior `/refresh` call |

#### Success response — 200 OK

Same shape as `/signin`:

```json
{
  "user": {
    "id": 42,
    "email": "user@example.com",
    "emailVerified": true,
    "displayName": "Jane Doe",
    "pictureUrl": "https://lh3.googleusercontent.com/...",
    "status": "ACTIVE"
  },
  "tokens": {
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...(new token)",
    "accessTokenExpiresAt": "2026-05-30T10:30:00Z",
    "refreshToken": "completely_new_refresh_token_value",
    "refreshTokenExpiresAt": "2026-06-29T10:15:00Z"
  },
  "newUser": false
}
```

`newUser` is always `false` on a refresh — no new account is being created.

**The old refresh token you submitted is now dead.** Replace the stored refresh token with the new one immediately. If you lose the new token before storing it, the user will need to sign in again.

#### Error responses

| HTTP status | `code` | When | Client action |
|---|---|---|---|
| 400 | `VALIDATION_FAILED` | `refreshToken` field is missing or blank | Fix the request |
| 400 | `MALFORMED_REQUEST` | Request body is not valid JSON | Fix the request |
| 401 | `INVALID_REFRESH_TOKEN` | Token not found in database, or already revoked (possible token theft or client bug) | Discard all stored tokens, redirect to sign-in |
| 401 | `REFRESH_TOKEN_EXPIRED` | Token is valid but past its 30-day TTL | Discard all stored tokens, redirect to sign-in |
| 403 | `USER_SUSPENDED` | Account was suspended after the refresh token was issued | Discard all stored tokens, show account-suspended message |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error | Retry once; if it persists, redirect to sign-in |

---

## Using the access token on protected endpoints

Every protected endpoint requires the access token in the `Authorization` header:

```
GET /api/v1/some-protected-resource
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json
```

If the header is absent, malformed, or the token is invalid/expired, the server returns:

```
HTTP 401 Unauthorized
```

with no body (Spring Security rejects the request before it reaches the application layer).

**Recommended client retry logic:**

```
Send request with access token
    │
    ├─ 2xx → success, done
    │
    └─ 401 → call POST /refresh with refresh token
                 │
                 ├─ 200 → store new tokens, retry original request once
                 │
                 └─ 401/403 → redirect to Google sign-in screen
```

---

## Error reference

Complete list of `code` values that can appear in error responses:

| Code | HTTP status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | A required field is missing, blank, or fails a size constraint |
| `MALFORMED_REQUEST` | 400 | The request body is not parseable as JSON |
| `INVALID_GOOGLE_TOKEN` | 401 | The Google ID token failed cryptographic verification |
| `INVALID_REFRESH_TOKEN` | 401 | The refresh token was not found or has already been used/revoked |
| `REFRESH_TOKEN_EXPIRED` | 401 | The refresh token exists but its 30-day TTL has passed |
| `USER_SUSPENDED` | 403 | The account is in a non-active state (suspended or deleted) |
| `INTERNAL_SERVER_ERROR` | 500 | An unexpected server error occurred |

---

## Development-only endpoints

These endpoints exist to aid local debugging. They must not be called by the production Android app.

### POST /api/v1/auth/google/verify

Verifies a Google ID token and returns the raw claims extracted from it. Does not touch the database, does not issue any tokens.

**Use case:** Confirming that the backend can verify a real Google token from your test device before testing the full sign-in flow.

#### Request

```
POST /api/v1/auth/google/verify
Content-Type: application/json
```

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6Ii..."
}
```

#### Success response — 200 OK

```json
{
  "subject": "109876543210987654321",
  "email": "user@example.com",
  "emailVerified": true,
  "displayName": "Jane Doe",
  "pictureUrl": "https://lh3.googleusercontent.com/..."
}
```

#### Error responses

Same as `/signin` — `INVALID_GOOGLE_TOKEN` on any verification failure.
