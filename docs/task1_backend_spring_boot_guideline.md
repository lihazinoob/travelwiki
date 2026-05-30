# AI Travel Planner Backend Blueprint — Spring Boot Folder Structure and Task 1 Implementation Guide

## Purpose of this document

This Markdown file is designed to be used as a long-term backend planning document for an AI-powered travel planning product based on the BUET Hackathon 2024 problem statement.

The current focus is **Task 1: Basic Itinerary Generation**.

The goal is not to build only a demo. The goal is to build a real deployable backend that can later support:

- AI itinerary generation
- budget estimation
- destination knowledge management
- transport planning
- map integration
- weather integration
- image album and semantic image search
- trip blog generation
- automated travel vlog generation

This document should be understandable by humans and other AI coding assistants.

**Always read `implementation_progress.md` alongside this document.** That file is the authoritative record of what has already been built, what decisions were made, and what comes next. This document covers design intent and plan; that document covers current reality.

---

# 1. High-level product backend idea

The backend should act as the **main brain** of the system.

The Android app should only collect user input and display the result. The backend should handle:

- user authentication
- request validation
- trip creation
- destination knowledge lookup
- transport option lookup
- cost estimation
- AI prompt preparation
- AI API communication
- AI JSON validation
- safe budget recalculation
- database persistence
- response formatting for the mobile app

The basic pipeline for Task 1 is:

```text
1. User fills the trip form
2. Android app sends a request to backend
3. Backend validates input
4. Backend calculates trip duration
5. Backend identifies destination
6. Backend loads destination knowledge from DB
7. Backend loads transport templates
8. Backend loads cost rules based on budget type
9. Backend prepares AI prompt
10. AI generates structured itinerary JSON
11. Backend validates AI JSON
12. Backend recalculates cost safely
13. Backend saves trip, days, items, and budget
14. Android app receives final response
15. App shows itinerary cards, budget cards, transport cards, meal cards
```

The most important engineering principle:

> AI should generate the plan and descriptions, but the backend should validate, calculate, control, and save the final result.

---

# 2. Recommended backend technology stack

## Core stack

- Java 17 or newer
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- Flyway for database migration
- Jakarta Bean Validation
- Lombok, optional
- MapStruct, optional
- Docker

## Dependencies already in use

- `io.jsonwebtoken:jjwt-api:0.12.6` (compile), `jjwt-impl` and `jjwt-jackson` (runtime) — JWT signing and validation
- `com.google.api-client:google-api-client` — Google ID token cryptographic verification

## Later additions

- Redis for caching destination/weather/place data
- pgvector for image/text semantic search
- Cloudinary, S3, or S3-compatible storage for media
- Firebase Cloud Messaging for weather notifications
- FFmpeg service for vlog generation

---

# 3. Global Spring Boot folder structure

Root package (actual project):

```text
com.example.travelwiki
```

Recommended folder structure:

```text
src/main/java/com/example/travelwiki
│
├── TravelWikiApplication.java
│
├── auth                          ✅ DONE — full Google OAuth + JWT layer implemented
│   ├── config
│   │   ├── GoogleAuthProperties.java
│   │   └── JwtProperties.java
│   ├── controller
│   │   └── AuthController.java
│   ├── dto
│   │   ├── GoogleAuthRequest.java
│   │   ├── RefreshRequest.java
│   │   ├── VerifiedGoogleToken.java
│   │   ├── AuthUserResponse.java
│   │   ├── AuthTokenPairResponse.java
│   │   └── AuthResponse.java
│   ├── entity
│   │   ├── User.java
│   │   ├── UserAuthIdentity.java
│   │   └── RefreshToken.java
│   ├── exception
│   │   ├── InvalidGoogleTokenException.java
│   │   ├── UserSuspendedException.java
│   │   ├── InvalidRefreshTokenException.java
│   │   └── RefreshTokenExpiredException.java
│   ├── repository
│   │   ├── UserRepository.java
│   │   ├── UserAuthIdentityRepository.java
│   │   └── RefreshTokenRepository.java
│   └── service
│       ├── GoogleTokenVerificationService.java
│       ├── GoogleTokenVerificationServiceImpl.java
│       ├── AuthService.java
│       ├── AuthServiceImpl.java
│       ├── JwtService.java
│       ├── JwtServiceImpl.java
│       ├── RefreshTokenService.java
│       └── RefreshTokenServiceImpl.java
│
├── security                      ✅ DONE
│   └── JwtAuthenticationFilter.java
│
├── config                        ✅ DONE
│   └── SecurityConfig.java
│
├── user                          ⏳ PENDING — user profile features post-Task 1
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── trip                          ⏳ NEXT — core Task 1 feature
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── mapper
│
├── itinerary                     ⏳ NEXT — core Task 1 feature
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   ├── ai
│   ├── prompt
│   ├── validator
│   └── mapper
│
├── destination                   ⏳ NEXT — core Task 1 feature
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── seed
│
├── transport                     ⏳ NEXT — core Task 1 feature
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── budget                        ⏳ NEXT — core Task 1 feature
│   ├── dto
│   ├── service
│   └── rules
│
├── ai                            ⏳ NEXT — core Task 1 feature
│   ├── client
│   ├── dto
│   ├── config
│   └── exception
│
├── weather                       ⏳ LATER — Task 3
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── places                        ⏳ LATER — Task 2
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── media                         ⏳ LATER — Task 5
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── storage
│
├── blog                          ⏳ LATER — Task 4
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── notification                  ⏳ LATER — Task 3
│   ├── dto
│   └── service
│
├── common                        ✅ PARTIALLY DONE
│   ├── config
│   ├── dto
│   ├── enums
│   ├── exception
│   │   ├── GlobalExceptionHandler.java   ✅ Done
│   │   └── ApiErrorResponse.java         ✅ Done
│   ├── response                          ⏳ Success wrapper pending
│   ├── util
│   └── validation
│
└── infra
    ├── database
    ├── external
    └── logging
```

> Note: the `budget` package does not need `entity` or `repository` subdirectories. Budget calculation is purely in-memory service logic; the result is persisted via the `trip_budgets` table which is owned by the `trip` package.

---

# 4. Auth layer — what is already built

**The entire auth layer is complete. Do not rebuild any part of it.**

## Auth approach

The project uses Google Sign-In only. There is no username/password registration or login.

Flow:
1. Android app obtains a Google `idToken` via the Google Sign-In SDK
2. Android sends `POST /api/v1/auth/google/signin` with the `idToken`
3. Backend verifies the `idToken` cryptographically using the Google Java client library
4. Backend finds or creates a local `User` record keyed on the Google `sub` claim
5. Backend issues a short-lived JWT access token (15 min TTL) and a server-stored rotatable refresh token (30 day TTL)
6. Android uses the JWT access token in `Authorization: Bearer <token>` on every protected request
7. When the access token expires, Android calls `POST /api/v1/auth/google/refresh` to get a new pair

## Auth configuration properties

The real config prefix is `auth.jwt` and `auth.google`, not `security.jwt`:

```yaml
auth:
  jwt:
    secret: ${JWT_SECRET}
    access-token-ttl-minutes: 15
    refresh-token-ttl-days: 30
  google:
    allowed-audiences:
      - ${GOOGLE_CLIENT_ID}
```

## Auth endpoints (already in api_reference.md)

```text
POST /api/v1/auth/google/signin    — sign in with Google idToken, returns JWT pair
POST /api/v1/auth/google/refresh   — rotate refresh token, returns new JWT pair
POST /api/v1/auth/google/verify    — dev-only: verify Google token without touching DB
```

## Key engineering decisions made during auth implementation

**Identity key is Google `sub`, not email.** Email can change; `sub` is permanent. The lookup is always `(provider=GOOGLE, provider_subject=sub)`.

**Refresh tokens are hashed.** Only the SHA-256 hash is stored. Raw token never touches the database.

**Rotation chain.** Each old refresh token records `replaced_by_token_id` pointing to its replacement. This allows tracing the full chain if a stolen token is detected.

**`SUSPENDED` and `DELETED` both return 403 `USER_SUSPENDED`.** Distinguishing them would allow account state enumeration.

**Never use PostgreSQL native `ENUM` types for JPA-mapped columns.** Use `VARCHAR`. Native enum types cause `operator does not exist: auth_provider = character varying` at runtime with Hibernate. This rule applies to all future migrations as well.

**`@Transactional` only on public methods.** Spring AOP does not intercept private methods. Private `@Transactional` annotations compile but are silently ignored.

**JPA dirty tracking instead of explicit `save()`.** Mutations on managed entities inside a `@Transactional` boundary auto-flush at commit. Only call `save()` for genuinely new (transient) entities.

---

# 5. Database migration state

Flyway is active. The following migrations already exist and must not be modified:

```text
V1__create_auth_tables.sql          — users, user_auth_identities, refresh_tokens
V2__add_missing_auth_columns.sql    — email_verified on users, email_at_auth_time on user_auth_identities
```

All new migrations for Task 1 features must start at **V3** and increment from there:

```text
V3__create_destination_tables.sql
V4__create_transport_and_cost_tables.sql
V5__create_trip_tables.sql
V6__seed_destinations.sql
V7__seed_transport_templates.sql
V8__seed_cost_rules.sql
```

---

# 6. Current exception handling state

The following is already implemented in `common/exception`:

**`ApiErrorResponse`** record: `success`, `code`, `message`, `path`, `timestamp`

**`ApiErrorCode`** enum (current):
- `VALIDATION_FAILED`
- `MALFORMED_REQUEST`
- `INVALID_GOOGLE_TOKEN`
- `USER_SUSPENDED`
- `INVALID_REFRESH_TOKEN`
- `REFRESH_TOKEN_EXPIRED`
- `INTERNAL_SERVER_ERROR`

**`GlobalExceptionHandler`** handles: `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `InvalidGoogleTokenException`, `UserSuspendedException`, `InvalidRefreshTokenException`, `RefreshTokenExpiredException`, and catch-all `Exception`.

When adding Task 1 feature exceptions, add new codes to `ApiErrorCode` and new handlers to `GlobalExceptionHandler`:

```text
DESTINATION_NOT_SUPPORTED   — destination alias not found in DB
TRIP_NOT_FOUND              — trip ID does not exist or belongs to another user
AI_PROVIDER_ERROR           — upstream AI API call failed
AI_RESPONSE_INVALID         — AI returned malformed or incomplete JSON
```

New exception classes to create:

```text
BadRequestException            → 400
NotFoundException              → 404
ForbiddenException             → 403
DestinationNotSupportedException → 400 with DESTINATION_NOT_SUPPORTED
AiProviderException            → 502 with AI_PROVIDER_ERROR
AiResponseValidationException  → 500 with AI_RESPONSE_INVALID
```

---

# 7. Current response envelope situation

**Error responses** already use a consistent wrapper via `ApiErrorResponse`:

```json
{
  "success": false,
  "code": "ERROR_CODE_STRING",
  "message": "Human-readable message",
  "path": "/api/v1/...",
  "timestamp": "2026-05-30T10:00:00Z"
}
```

**Success responses** currently return the data object directly (no wrapper). For example, the auth endpoints return `AuthResponse` as the root object.

For Task 1 trip endpoints, wrap success responses in `ApiResponse<T>`:

```java
public record ApiResponse<T>(
    boolean success,
    String message,
    T data
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
}
```

Place this in `common/response/ApiResponse.java`.

The trip controller should use it:

```java
return ResponseEntity.ok(ApiResponse.success("Trip itinerary generated successfully", response));
```

The auth endpoints can be migrated to this wrapper later if desired; do not change them now.

---

# 8. Task 1 backend scope

Task 1 is about generating a basic travel itinerary.

The backend must support:

- trip form submission
- request validation
- trip duration calculation
- destination identification
- destination knowledge loading
- transport template loading
- budget rule loading
- AI prompt creation
- AI itinerary generation
- AI JSON validation
- cost recalculation
- database persistence
- response to Android app

---

# 9. Task 1 backend flow in detail

## Step 1: User fills the trip form

This happens in the Android app.

Example fields:

```text
destination
startLocation
startDate
endDate
travelerCount
budgetType
preferences
specialNotes
```

Example request from Android:

```json
{
  "destination": "Saint Martin",
  "startLocation": "Dhaka",
  "startDate": "2026-06-10",
  "endDate": "2026-06-12",
  "travelerCount": 3,
  "budgetType": "MID_RANGE",
  "preferences": ["beach", "seafood", "photography", "relaxing"],
  "specialNotes": "We want a relaxed trip after exams."
}
```

---

## Step 2: Android app sends a request to backend

Endpoint:

```http
POST /api/v1/trips/generate
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

The endpoint creates a trip and generates the itinerary in one flow for the MVP.

Later, this can be split into:

```http
POST /api/v1/trips
POST /api/v1/trips/{tripId}/generate-itinerary
```

---

## Step 3: Backend validates input

Use Jakarta Bean Validation annotations on the request DTO.

```java
public record GenerateTripRequest(
    @NotBlank(message = "Destination is required")
    String destination,

    @NotBlank(message = "Start location is required")
    String startLocation,

    @NotNull(message = "Start date is required")
    LocalDate startDate,

    @NotNull(message = "End date is required")
    LocalDate endDate,

    @Min(value = 1, message = "At least one traveler is required")
    @Max(value = 20, message = "Maximum 20 travelers are allowed")
    int travelerCount,

    @NotNull(message = "Budget type is required")
    BudgetType budgetType,

    List<String> preferences,

    String specialNotes
) {}
```

Note: `int travelerCount` cannot be `@NotNull` (it is a primitive). If the field is omitted in the JSON body it defaults to 0, which the `@Min(1)` catches correctly. This is intentional.

Additional service-level validation in `TripRequestValidator`:

```text
- endDate must not be before startDate
- trip duration must be at least 1 day
- trip duration must not exceed 30 days
```

Example:

```java
public void validate(GenerateTripRequest request) {
    if (request.endDate().isBefore(request.startDate())) {
        throw new BadRequestException("End date cannot be before start date");
    }
    long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
    if (days < 1) {
        throw new BadRequestException("Trip duration must be at least 1 day");
    }
    if (days > 30) {
        throw new BadRequestException("Trip duration cannot exceed 30 days");
    }
}
```

---

## Step 4: Backend calculates trip duration

```java
public record TripDuration(long tripDays, long tripNights) {}
```

Formula:

```java
long tripDays = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
long tripNights = Math.max(tripDays - 1, 0);
return new TripDuration(tripDays, tripNights);
```

Example:

```text
startDate: 2026-06-10
endDate:   2026-06-12
tripDays  = 3
tripNights = 2
```

---

## Step 5: Backend identifies destination

The backend normalizes the user input to a known destination record.

Examples that all map to the same record:

```text
"saint martin"
"Saint Martin"
"St. Martin"
"Saint Martin Island"
```

All resolve to destination code `SAINT_MARTIN`.

MVP approach: store aliases in the `destination_aliases` table. Match by lowercased input against lowercased alias values.

If no alias matches, throw `DestinationNotSupportedException`.

---

## Step 6: Backend loads destination knowledge from DB

Destination knowledge is the curated context fed into the AI prompt.

Example for Saint Martin:

```text
Destination: Saint Martin
Type: Island / Beach
Recommended duration: 2–4 days
Best for: beach, seafood, photography, relaxation
Popular activities: beach walk, sunrise viewing, sunset viewing, seafood dinner, cycling, local market, coral beach
Local tips:
  - Ship schedules depend on weather and season
  - Keep buffer time for transport
  - Carry cash; digital payment may not be available everywhere
```

The service loads both the `destinations` row and all related `destination_activities` rows, then combines them into a `DestinationContext`:

```java
public record DestinationContext(
    Destination destination,
    List<DestinationActivity> activities
) {}
```

The `local_tips` and `best_for` fields on the `destinations` table are plain text and are included directly in the AI prompt.

Destination table:

```sql
CREATE TABLE destinations (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) UNIQUE NOT NULL,
    name VARCHAR(120) NOT NULL,
    country VARCHAR(80),
    region VARCHAR(120),
    description TEXT,
    destination_type VARCHAR(80),
    recommended_min_days INT,
    recommended_max_days INT,
    best_for TEXT,
    local_tips TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

Activities table:

```sql
CREATE TABLE destination_activities (
    id BIGSERIAL PRIMARY KEY,
    destination_id BIGINT NOT NULL REFERENCES destinations(id),
    title VARCHAR(150) NOT NULL,
    description TEXT,
    category VARCHAR(80),
    estimated_cost_min NUMERIC(12, 2),
    estimated_cost_max NUMERIC(12, 2),
    recommended_duration_minutes INT,
    priority INT DEFAULT 0
);
```

Alias table:

```sql
CREATE TABLE destination_aliases (
    id BIGSERIAL PRIMARY KEY,
    destination_id BIGINT NOT NULL REFERENCES destinations(id),
    alias VARCHAR(150) NOT NULL UNIQUE
);
```

---

## Step 7: Backend loads transport templates

Transport templates describe how to reach a destination from a starting point for a given budget type.

Example for Dhaka → Saint Martin:

```text
BUDGET:     Dhaka → Cox's Bazar/Teknaf by bus → ship to Saint Martin
MID_RANGE:  Dhaka → Cox's Bazar by AC bus or flight → Teknaf by road → ship
LUXURY:     Dhaka → Cox's Bazar by flight → private transfer → ship
```

Table:

```sql
CREATE TABLE transport_templates (
    id BIGSERIAL PRIMARY KEY,
    from_location VARCHAR(120) NOT NULL,
    to_destination_id BIGINT NOT NULL REFERENCES destinations(id),
    budget_type VARCHAR(40) NOT NULL,
    title VARCHAR(150) NOT NULL,
    route_summary TEXT NOT NULL,
    estimated_time_min_minutes INT,
    estimated_time_max_minutes INT,
    cost_min_per_person NUMERIC(12, 2),
    cost_max_per_person NUMERIC(12, 2),
    notes TEXT,
    is_active BOOLEAN DEFAULT TRUE
);
```

The service matches on `from_location` (case-insensitive), `to_destination_id`, and `budget_type`. If no exact match exists, fall back to the `MID_RANGE` template for the destination.

---

## Step 8: Backend loads cost rules and estimates budget

The backend calculates a preliminary budget from database rules before calling AI, and then recalculates the final budget from the same rules after AI returns. AI-generated cost figures are never used for the final budget.

Cost rules table:

```sql
CREATE TABLE cost_rules (
    id BIGSERIAL PRIMARY KEY,
    destination_id BIGINT REFERENCES destinations(id),
    budget_type VARCHAR(40) NOT NULL,
    food_min_per_person_per_day NUMERIC(12, 2),
    food_max_per_person_per_day NUMERIC(12, 2),
    accommodation_min_per_room_per_night NUMERIC(12, 2),
    accommodation_max_per_room_per_night NUMERIC(12, 2),
    activity_min_per_person_per_day NUMERIC(12, 2),
    activity_max_per_person_per_day NUMERIC(12, 2),
    misc_min_per_person_per_day NUMERIC(12, 2),
    misc_max_per_person_per_day NUMERIC(12, 2)
);
```

Budget formulas:

```text
roomsNeeded       = ceil(travelerCount / 2.0)

foodCost          = midpoint(food_min, food_max) * travelerCount * tripDays
accommodationCost = midpoint(acc_min, acc_max) * roomsNeeded * tripNights
transportCost     = midpoint(transport_cost_min, transport_cost_max) * travelerCount
activityCost      = midpoint(activity_min, activity_max) * travelerCount * tripDays
miscCost          = midpoint(misc_min, misc_max) * travelerCount * tripDays

subtotal          = foodCost + accommodationCost + transportCost + activityCost + miscCost
bufferCost        = subtotal * 0.10
totalCost         = subtotal + bufferCost
perPersonCost     = totalCost / travelerCount
```

Where `midpoint(min, max) = (min + max) / 2`.

Budget estimate output DTO:

```java
public record BudgetEstimate(
    BigDecimal transportCost,
    BigDecimal foodCost,
    BigDecimal accommodationCost,
    BigDecimal activityCost,
    BigDecimal miscCost,
    BigDecimal bufferCost,
    BigDecimal totalCost,
    BigDecimal perPersonCost,
    String currency
) {}
```

---

## Step 9: Backend prepares AI prompt

The prompt builder assembles everything into a single string sent to the AI model.

Prompt builder input:

```java
public record ItineraryPromptContext(
    GenerateTripRequest request,
    DestinationContext destinationContext,
    TransportTemplate transportTemplate,
    BudgetEstimate preliminaryBudget,
    TripDuration duration
) {}
```

The prompt must include the full required JSON schema inline so the AI knows exactly what structure to return.

Complete prompt template:

```text
You are a travel itinerary planning assistant for Bangladesh destinations.

Create a practical travel itinerary using only the provided context.
Do not invent exact hotel names unless provided.
Do not invent exact live ticket prices.
Use the provided cost estimates as the budget boundary.
Return ONLY valid JSON matching the schema below. No explanation, no markdown, no preamble.

=== USER REQUEST ===
Destination: {destination}
Starting location: {startLocation}
Trip days: {tripDays}
Trip nights: {tripNights}
Travelers: {travelerCount}
Budget type: {budgetType}
Preferences: {preferences}
Special notes: {specialNotes}

=== DESTINATION CONTEXT ===
Type: {destinationType}
Best for: {bestFor}
Local tips: {localTips}
Popular activities: {activitiesList}

=== TRANSPORT CONTEXT ===
Route: {routeSummary}
Estimated time: {estimatedTimeRange}
Estimated transport cost per person: {transportCostRange} BDT

=== BUDGET ESTIMATE ===
Transport: {transportCost} BDT
Food: {foodCost} BDT
Accommodation: {accommodationCost} BDT
Activities: {activityCost} BDT
Misc: {miscCost} BDT
Estimated total: {totalCost} BDT

=== REQUIRED JSON SCHEMA ===
{
  "title": "string",
  "summary": "string",
  "destination": "string",
  "startLocation": "string",
  "tripDays": number,
  "tripNights": number,
  "travelerCount": number,
  "budgetType": "BUDGET|MID_RANGE|LUXURY",
  "transportPlan": {
    "title": "string",
    "summary": "string",
    "estimatedTime": "string",
    "notes": ["string"]
  },
  "days": [
    {
      "dayNumber": number,
      "title": "string",
      "summary": "string",
      "items": [
        {
          "time": "HH:MM",
          "category": "TRANSPORT|FOOD|ACCOMMODATION|ACTIVITY|REST|SHOPPING|BUFFER|OTHER",
          "title": "string",
          "description": "string",
          "locationName": "string",
          "estimatedCost": number,
          "durationMinutes": number
        }
      ]
    }
  ],
  "mealPlan": [
    {
      "dayNumber": number,
      "breakfast": "string",
      "lunch": "string",
      "dinner": "string"
    }
  ],
  "accommodationSuggestion": {
    "type": "string",
    "description": "string",
    "estimatedCostPerRoomPerNight": number
  },
  "tips": ["string"]
}
```

---

## Step 10: AI generates structured itinerary JSON

### AI provider for MVP

Use **OpenAI GPT-4o-mini** via the OpenAI REST API. This is the recommended choice for the MVP because it is cost-effective, fast, and reliable for structured JSON output.

API call:

```java
POST https://api.openai.com/v1/chat/completions
Authorization: Bearer ${OPENAI_API_KEY}
Content-Type: application/json

{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "user", "content": "<the full prompt string>" }
  ],
  "temperature": 0.7,
  "response_format": { "type": "json_object" }
}
```

Using `"response_format": { "type": "json_object" }` forces the model to return only valid JSON with no markdown wrapper. This is strongly recommended.

Configuration in `application.yml`:

```yaml
ai:
  provider: openai
  api-key: ${OPENAI_API_KEY}
  model: gpt-4o-mini
  timeout-seconds: 60
  max-retries: 1
```

The `AiClient` in `ai/client/AiClient.java` should use Spring's `RestClient` or `WebClient` to call this endpoint. Set a timeout of 60 seconds. Do not use the official OpenAI Java SDK for the MVP — a plain HTTP call is simpler and has no extra dependency.

Important: The AI may include item-level `estimatedCost` values inside the `days` array. These are used only for display hints in the itinerary cards. The backend always recalculates the final budget independently.

---

## Step 11: Backend validates AI JSON

After receiving the raw response string from the AI, parse it and validate the structure before doing anything else.

```java
// AiItineraryValidator.java
public void validate(AiItineraryResponse response, long expectedTripDays) {
    if (response == null) {
        throw new AiResponseValidationException("AI response is null");
    }
    if (response.title() == null || response.title().isBlank()) {
        throw new AiResponseValidationException("AI response missing title");
    }
    if (response.days() == null || response.days().isEmpty()) {
        throw new AiResponseValidationException("AI response contains no days");
    }
    if (response.days().size() != expectedTripDays) {
        throw new AiResponseValidationException(
            "AI day count " + response.days().size() + " does not match expected " + expectedTripDays
        );
    }
    for (AiDayResponse day : response.days()) {
        if (day.items() == null || day.items().isEmpty()) {
            throw new AiResponseValidationException("Day " + day.dayNumber() + " has no items");
        }
    }
}
```

**Retry strategy:**

1. Parse and validate the response
2. If validation fails, retry once with `temperature=0` and the full schema embedded more explicitly
3. If the second attempt also fails, throw `AiResponseValidationException` and return a clean error to the client
4. Never persist a failed or partial itinerary

---

## Step 12: Backend recalculates cost safely

After AI validation succeeds, recalculate the final budget using the same `BudgetEstimationService` as in Step 8. This is the budget that gets persisted and returned to the client.

Reason: AI may produce inconsistent or hallucinated cost figures. The backend's rule-based calculation is the source of truth for all financial data.

---

## Step 13: Backend saves trip, days, items, and budget

All persistence happens inside a single `@Transactional` boundary in `TripPersistenceService`.

### trips

```sql
CREATE TABLE trips (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    destination_id BIGINT REFERENCES destinations(id),
    destination_name VARCHAR(150) NOT NULL,
    start_location VARCHAR(150) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    trip_days INT NOT NULL,
    trip_nights INT NOT NULL,
    traveler_count INT NOT NULL,
    budget_type VARCHAR(40) NOT NULL,
    preferences TEXT,
    special_notes TEXT,
    title VARCHAR(200),
    summary TEXT,
    status VARCHAR(40) DEFAULT 'GENERATED',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

**Security rule:** Every query on the `trips` table that loads a trip by ID must also filter by `user_id`. Never load a trip by ID alone. If the `user_id` does not match the authenticated user, throw `ForbiddenException` (not `NotFoundException`, to avoid leaking trip existence).

### itinerary_days

```sql
CREATE TABLE itinerary_days (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    day_number INT NOT NULL,
    title VARCHAR(200),
    summary TEXT,
    date DATE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

### itinerary_items

```sql
CREATE TABLE itinerary_items (
    id BIGSERIAL PRIMARY KEY,
    itinerary_day_id BIGINT NOT NULL REFERENCES itinerary_days(id) ON DELETE CASCADE,
    order_index INT NOT NULL,
    time_label VARCHAR(40),
    category VARCHAR(60),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    location_name VARCHAR(200),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    estimated_cost NUMERIC(12, 2),
    duration_minutes INT
);
```

### trip_budgets

```sql
CREATE TABLE trip_budgets (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    transport_cost NUMERIC(12, 2) NOT NULL,
    food_cost NUMERIC(12, 2) NOT NULL,
    accommodation_cost NUMERIC(12, 2) NOT NULL,
    activity_cost NUMERIC(12, 2) NOT NULL,
    misc_cost NUMERIC(12, 2) NOT NULL,
    buffer_cost NUMERIC(12, 2) NOT NULL,
    total_cost NUMERIC(12, 2) NOT NULL,
    per_person_cost NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'BDT',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

### trip_meal_plans

```sql
CREATE TABLE trip_meal_plans (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    day_number INT NOT NULL,
    breakfast TEXT,
    lunch TEXT,
    dinner TEXT
);
```

### trip_accommodation_suggestions

```sql
CREATE TABLE trip_accommodation_suggestions (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    accommodation_type VARCHAR(100),
    description TEXT,
    estimated_cost_per_room_per_night NUMERIC(12, 2)
);
```

---

## Step 14: Android app receives final response

```json
{
  "success": true,
  "message": "Trip itinerary generated successfully",
  "data": {
    "tripId": 1,
    "title": "3-Day Saint Martin Trip",
    "summary": "A relaxed beach-focused trip with seafood, photography, and rest time.",
    "destination": "Saint Martin",
    "startLocation": "Dhaka",
    "startDate": "2026-06-10",
    "endDate": "2026-06-12",
    "tripDays": 3,
    "tripNights": 2,
    "travelerCount": 3,
    "budgetType": "MID_RANGE",
    "budget": {
      "transportCost": 15000,
      "foodCost": 9000,
      "accommodationCost": 12000,
      "activityCost": 4500,
      "miscCost": 3000,
      "bufferCost": 4350,
      "totalCost": 47850,
      "perPersonCost": 15950,
      "currency": "BDT"
    },
    "transportPlan": {
      "title": "Mid-range route to Saint Martin",
      "summary": "Travel from Dhaka to Cox's Bazar/Teknaf, then take a ship to Saint Martin.",
      "estimatedTime": "12-16 hours",
      "notes": [
        "Keep buffer time for ship schedule changes.",
        "Weather may affect sea transport."
      ]
    },
    "days": [
      {
        "dayNumber": 1,
        "date": "2026-06-10",
        "title": "Journey and Arrival",
        "summary": "Travel to Saint Martin and enjoy a relaxed evening near the beach.",
        "items": [
          {
            "time": "06:00",
            "category": "TRANSPORT",
            "title": "Start journey from Dhaka",
            "description": "Begin the journey toward Teknaf/Cox's Bazar route.",
            "locationName": "Dhaka",
            "estimatedCost": 5000,
            "durationMinutes": 480
          }
        ]
      }
    ],
    "mealPlan": [
      {
        "dayNumber": 1,
        "breakfast": "Simple breakfast before departure.",
        "lunch": "Local meal during travel break.",
        "dinner": "Seafood dinner near the beach."
      }
    ],
    "accommodationSuggestion": {
      "type": "MID_RANGE_HOTEL_OR_RESORT",
      "description": "Choose a clean mid-range hotel or resort close to the beach.",
      "estimatedCostPerRoomPerNight": 3500
    },
    "tips": [
      "Carry cash for local expenses.",
      "Keep extra time for transport delays."
    ]
  }
}
```

---

## Step 15: App shows itinerary cards, budget cards, transport cards, meal cards

The backend returns mobile-friendly data structured so the Android app can directly render:

```text
Trip header card
Budget breakdown card
Transport card
Day 1 itinerary card
Day 2 itinerary card
Day 3 itinerary card
Meal plan card
Accommodation suggestion card
Tips card
```

---

# 10. Recommended API endpoints for Task 1

## Generate trip itinerary

```http
POST /api/v1/trips/generate
Authorization: Bearer <jwt_token>
```

Request body: `GenerateTripRequest`

Response: `ApiResponse<TripDetailResponse>`

## Get all trips of logged-in user

```http
GET /api/v1/trips
Authorization: Bearer <jwt_token>
```

Response: `ApiResponse<List<TripSummaryResponse>>`

## Get single trip details

```http
GET /api/v1/trips/{tripId}
Authorization: Bearer <jwt_token>
```

Response: `ApiResponse<TripDetailResponse>`

## Delete trip

```http
DELETE /api/v1/trips/{tripId}
Authorization: Bearer <jwt_token>
```

Response: `ApiResponse<Void>` or 204 No Content

---

# 11. Main backend classes for Task 1

## TripController

```text
trip/controller/TripController.java
```

Resolves the authenticated `userId` from `SecurityContextHolder`:

```java
Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
```

Example generate endpoint:

```java
@PostMapping("/generate")
public ResponseEntity<ApiResponse<TripDetailResponse>> generateTrip(
    @Valid @RequestBody GenerateTripRequest request
) {
    Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    TripDetailResponse response = tripGenerationService.generateTrip(request, userId);
    return ResponseEntity.ok(ApiResponse.success("Trip itinerary generated successfully", response));
}
```

---

## TripGenerationService (orchestrator)

```text
trip/service/TripGenerationService.java
```

Controls the full pipeline. Pseudocode:

```java
@Transactional
public TripDetailResponse generateTrip(GenerateTripRequest request, Long userId) {

    tripRequestValidator.validate(request);

    TripDuration duration = TripDuration.from(request.startDate(), request.endDate());

    Destination destination = destinationService.resolveDestination(request.destination());

    DestinationContext destinationContext = destinationService.loadContext(destination.getId());

    TransportTemplate transportTemplate = transportService.findBestTemplate(
        request.startLocation(), destination.getId(), request.budgetType()
    );

    BudgetEstimate preliminaryBudget = budgetEstimationService.estimate(
        request, destination, transportTemplate, duration
    );

    String prompt = itineraryPromptBuilder.build(
        new ItineraryPromptContext(request, destinationContext, transportTemplate, preliminaryBudget, duration)
    );

    AiItineraryResponse aiResponse = aiItineraryService.generate(prompt);

    aiItineraryValidator.validate(aiResponse, duration.tripDays());

    BudgetEstimate finalBudget = budgetEstimationService.estimate(
        request, destination, transportTemplate, duration
    );

    Trip trip = tripPersistenceService.saveGeneratedTrip(
        userId, request, destination, duration, aiResponse, finalBudget
    );

    return tripQueryService.getTripDetails(trip.getId(), userId);
}
```

---

## DestinationService

```text
destination/service/DestinationService.java
```

```java
Destination resolveDestination(String userInput);
DestinationContext loadContext(Long destinationId);
```

`resolveDestination` lowercases the input and queries `destination_aliases` for a match. Throws `DestinationNotSupportedException` if no alias matches.

---

## TransportService

```text
transport/service/TransportService.java
```

```java
TransportTemplate findBestTemplate(String startLocation, Long destinationId, BudgetType budgetType);
```

Falls back to `MID_RANGE` template if no exact `budgetType` match exists for the given route.

---

## BudgetEstimationService

```text
budget/service/BudgetEstimationService.java
```

```java
BudgetEstimate estimate(
    GenerateTripRequest request,
    Destination destination,
    TransportTemplate transportTemplate,
    TripDuration duration
);
```

Loads the `CostRule` for `(destination_id, budget_type)` and applies the formulas from Step 8.

---

## ItineraryPromptBuilder

```text
itinerary/prompt/ItineraryPromptBuilder.java
```

Takes an `ItineraryPromptContext` and returns the complete prompt string, with the full JSON schema embedded inline (see Step 9 template).

---

## AiItineraryService

```text
itinerary/ai/AiItineraryService.java
```

```java
AiItineraryResponse generate(String prompt);
```

Calls `AiClient`, receives the raw JSON string, parses it into `AiItineraryResponse`. On parse failure, retries once with a lower temperature. Throws `AiProviderException` on HTTP failure, `AiResponseValidationException` on parse failure after retry.

---

## AiClient

```text
ai/client/AiClient.java
```

Direct HTTP communication with the AI provider. Configured timeout, API key injection, error handling. Returns the raw response body string. Does not parse JSON.

Important: The OpenAI API key must never be exposed to the Android app. The client reads it from `${OPENAI_API_KEY}` environment variable only.

---

## AiItineraryValidator

```text
itinerary/validator/AiItineraryValidator.java
```

Validates structure and content of the parsed `AiItineraryResponse` (see Step 11).

---

## TripPersistenceService

```text
trip/service/TripPersistenceService.java
```

Saves `Trip`, `ItineraryDay`, `ItineraryItem`, `TripBudget`, `TripMealPlan`, and `TripAccommodationSuggestion` within a single `@Transactional` boundary.

---

## TripQueryService

```text
trip/service/TripQueryService.java
```

Loads a saved trip by ID, enforces `user_id` ownership check, maps entities to `TripDetailResponse`.

---

# 12. Important DTOs for Task 1

## GenerateTripRequest

```java
public record GenerateTripRequest(
    String destination,
    String startLocation,
    LocalDate startDate,
    LocalDate endDate,
    int travelerCount,
    BudgetType budgetType,
    List<String> preferences,
    String specialNotes
) {}
```

## TripDetailResponse

```java
public record TripDetailResponse(
    Long tripId,
    String title,
    String summary,
    String destination,
    String startLocation,
    LocalDate startDate,
    LocalDate endDate,
    int tripDays,
    int tripNights,
    int travelerCount,
    BudgetType budgetType,
    BudgetResponse budget,
    TransportPlanResponse transportPlan,
    List<ItineraryDayResponse> days,
    List<MealPlanResponse> mealPlan,
    AccommodationSuggestionResponse accommodationSuggestion,
    List<String> tips
) {}
```

## TripSummaryResponse

```java
public record TripSummaryResponse(
    Long tripId,
    String title,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    int tripDays,
    BudgetType budgetType,
    BigDecimal totalCost,
    String currency
) {}
```

## BudgetResponse

```java
public record BudgetResponse(
    BigDecimal transportCost,
    BigDecimal foodCost,
    BigDecimal accommodationCost,
    BigDecimal activityCost,
    BigDecimal miscCost,
    BigDecimal bufferCost,
    BigDecimal totalCost,
    BigDecimal perPersonCost,
    String currency
) {}
```

## ItineraryDayResponse

```java
public record ItineraryDayResponse(
    int dayNumber,
    LocalDate date,
    String title,
    String summary,
    List<ItineraryItemResponse> items
) {}
```

## ItineraryItemResponse

```java
public record ItineraryItemResponse(
    String time,
    ItineraryItemCategory category,
    String title,
    String description,
    String locationName,
    BigDecimal estimatedCost,
    Integer durationMinutes
) {}
```

---

# 13. Important enums

```java
public enum BudgetType {
    BUDGET,
    MID_RANGE,
    LUXURY
}
```

```java
public enum TripStatus {
    DRAFT,
    GENERATING,
    GENERATED,
    FAILED,
    COMPLETED
}
```

```java
public enum ItineraryItemCategory {
    TRANSPORT,
    FOOD,
    ACCOMMODATION,
    ACTIVITY,
    REST,
    SHOPPING,
    BUFFER,
    OTHER
}
```

---

# 14. Application properties structure

Add the following to `application.yml` (merge with existing auth and datasource config already present):

```yaml
ai:
  provider: openai
  api-key: ${OPENAI_API_KEY}
  model: gpt-4o-mini
  timeout-seconds: 60
  max-retries: 1
```

The full file should look like:

```yaml
spring:
  application:
    name: travelwiki

  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true

  flyway:
    enabled: true

server:
  port: ${SERVER_PORT:8080}

auth:
  jwt:
    secret: ${JWT_SECRET}
    access-token-ttl-minutes: 15
    refresh-token-ttl-days: 30
  google:
    allowed-audiences:
      - ${GOOGLE_CLIENT_ID}

ai:
  provider: openai
  api-key: ${OPENAI_API_KEY}
  model: gpt-4o-mini
  timeout-seconds: 60
  max-retries: 1
```

Never hardcode secrets in source code or commit them to version control.

---

# 15. Development order for Task 1

Stages 1 and 2 are fully complete. Start from Stage 3.

## Stage 1: Project setup ✅ DONE

```text
✅ Spring Boot project created
✅ Dependencies added (JPA, Security, Flyway, jjwt, Google client)
✅ PostgreSQL connected
✅ Flyway active (V1 and V2 migrations applied)
✅ Global exception handler
```

## Stage 2: Auth foundation ✅ DONE

```text
✅ User entity, UserAuthIdentity entity, RefreshToken entity
✅ Google sign-in endpoint (POST /api/v1/auth/google/signin)
✅ JWT generation and validation (JwtService)
✅ JWT authentication filter (JwtAuthenticationFilter)
✅ Refresh token endpoint (POST /api/v1/auth/google/refresh)
✅ Spring Security configuration
✅ All auth endpoints end-to-end tested
```

## Stage 3: Global success response wrapper ⏳ NEXT (small, do first)

```text
1. Create ApiResponse<T> record in common/response/ApiResponse.java
2. Verify existing auth endpoints still work (they don't use the wrapper; leave them as-is)
3. All new trip endpoints will use ApiResponse<T> from this point on
```

## Stage 4: Destination foundation

```text
1. Destination entity
2. DestinationActivity entity
3. DestinationAlias entity
4. Destination repositories
5. DestinationService (resolveDestination, loadContext)
6. V3 migration: create destination tables
7. V6 migration: seed Saint Martin data (destination + aliases + activities)
```

## Stage 5: Transport and cost rules

```text
1. TransportTemplate entity
2. CostRule entity
3. TransportService (findBestTemplate)
4. BudgetEstimationService (estimate)
5. V4 migration: create transport_templates and cost_rules tables
6. V7 migration: seed transport templates from Dhaka to Saint Martin (all 3 budget types)
7. V8 migration: seed cost rules for Saint Martin (all 3 budget types)
8. Unit test BudgetEstimationService with known inputs
```

## Stage 6: Trip and itinerary persistence

```text
1. Trip entity
2. ItineraryDay entity
3. ItineraryItem entity
4. TripBudget entity
5. TripMealPlan entity
6. TripAccommodationSuggestion entity
7. All repositories
8. TripPersistenceService
9. TripQueryService
10. V5 migration: create all trip tables
```

## Stage 7: AI integration

```text
1. AiClient (OpenAI REST call via RestClient, 60s timeout)
2. AiItineraryResponse DTO (mirrors the JSON schema in Step 9)
3. AiItineraryService (call client, parse response, retry once on failure)
4. ItineraryPromptBuilder (builds full prompt string from ItineraryPromptContext)
5. AiItineraryValidator (structural validation)
```

## Stage 8: Generate trip API — wire everything together

```text
1. TripRequestValidator
2. TripGenerationService (orchestrates Steps 3–13)
3. TripController (POST /generate, GET /, GET /{id}, DELETE /{id})
4. Add new error codes to ApiErrorCode
5. Add new exception handlers to GlobalExceptionHandler
```

## Stage 9: Testing and hardening

```text
1. Test invalid date range (endDate before startDate)
2. Test unsupported destination (no alias match)
3. Test budget calculation with known numbers
4. Test AI invalid JSON fallback (retry + clean error)
5. Test user cannot GET or DELETE another user's trip (expect 403)
6. Test full generate flow end-to-end with a real Saint Martin request
7. Test GET /api/v1/trips returns only the logged-in user's trips
```

---

# 16. Minimum working Task 1 MVP

The following endpoints are needed for the first complete version:

```text
POST /api/v1/auth/google/signin     ✅ Done
POST /api/v1/auth/google/refresh    ✅ Done
POST /api/v1/trips/generate         ⏳ Next
GET  /api/v1/trips                  ⏳ Next
GET  /api/v1/trips/{tripId}         ⏳ Next
DELETE /api/v1/trips/{tripId}       ⏳ Next
```

The first version only needs one destination to work well:

```text
Saint Martin
```

Once Saint Martin works end-to-end, add more destinations by seeding additional rows. No code changes required.

---

# 17. What not to build first

Do not start with:

```text
- maps or geocoding
- weather notifications
- image upload
- semantic image search
- blog generation
- vlog generation
- hotel booking
- payment
- admin dashboard
- user profile editing
```

First success condition:

```text
A logged-in user can POST /api/v1/trips/generate with a Saint Martin request,
receive a structured itinerary with a backend-calculated budget breakdown,
and later reopen the saved trip via GET /api/v1/trips/{tripId}.
```

---

# 18. Backend responsibility split

## Android app responsibility

```text
- obtain Google idToken via Sign-In SDK
- call POST /api/v1/auth/google/signin
- store JWT access token and refresh token securely
- send access token in Authorization header on every request
- refresh tokens when 401 is received
- collect trip form data
- display loading state during itinerary generation
- display generated itinerary cards
- show errors from API response
```

## Backend responsibility

```text
- verify Google idToken cryptographically
- find or create local user
- issue and rotate JWT tokens
- authenticate and authorize every protected request
- validate trip request
- calculate duration
- resolve destination
- load destination knowledge
- load transport and cost rules
- build AI prompt
- call AI API
- validate AI response
- recalculate budget from rules (never trust AI totals)
- save generated trip with all related data
- return structured mobile-friendly response
- enforce that users can only access their own trips
```

## AI responsibility

```text
- generate human-friendly itinerary text and summaries
- organize daily activities in a logical order
- create meal suggestions based on destination and preferences
- produce travel tips based on destination context
- format output according to the provided JSON schema
```

## Database responsibility

```text
- store users and auth identities
- store destinations, aliases, and activities
- store transport templates and cost rules
- store generated trips with itinerary days and items
- store budget breakdown per trip
- store meal plans and accommodation suggestions
```

---

# 19. Important engineering principles

## Principle 1: AI output must be structured JSON

Instruct the model explicitly and use `response_format: json_object` where supported.

## Principle 2: Backend must recalculate budget

Never use AI-generated totals. The `BudgetEstimationService` is the single source of truth.

## Principle 3: Save generated result

Do not regenerate every time the user opens the trip. Generate once, persist, serve from DB.

## Principle 4: Keep AI client isolated

`AiClient` only does HTTP. It does not know about trips, destinations, or budgets. `AiItineraryService` wraps it with retry and parsing logic. Business orchestration lives in `TripGenerationService`.

## Principle 5: Use database seed data first

Do not call external APIs (weather, geocoding, hotels) in the MVP. All context comes from seeded DB rows.

## Principle 6: Make response mobile-friendly

Return clean arrays and objects. The Android app renders cards directly from the JSON; it should not need to parse strings or split text.

## Principle 7: Never use PostgreSQL native enum types

Always use `VARCHAR` in DDL for columns mapped with `@Enumerated(EnumType.STRING)`. See the P2 issue in `implementation_progress.md` for the full explanation.

## Principle 8: Enforce user ownership on every trip query

Every `SELECT`, `UPDATE`, or `DELETE` on the `trips` table must include `AND user_id = :userId`. A missing ownership check is a security vulnerability, not just a bug.

---

# 20. Future extension path

After Task 1 is complete, extend in this order:

## Task 2: Map Integration

```text
- latitude/longitude fields on itinerary_items are already in the schema
- add Google Places or OpenStreetMap lookup for item coordinates
- add map pins endpoint: GET /api/v1/trips/{tripId}/map
- route visualization support
```

## Task 3: Weather Notifications

```text
- weather API integration (OpenWeatherMap)
- weather snapshot table
- weather risk detection (rain/storm alerts)
- Firebase Cloud Messaging notification service
```

## Task 4: Trip Blog Generation

```text
- trip blog table
- blog prompt builder (uses saved trip content)
- POST /api/v1/trips/{tripId}/blog
- share/export support
```

## Task 5: Image Upload and Textual Search

```text
- media upload (S3 or Cloudinary)
- album table linked to trips
- image captioning via AI
- text embeddings (pgvector)
- POST /api/v1/trips/{tripId}/albums
- GET /api/v1/trips/{tripId}/albums/search?q=...
```

## Bonus: Automated Vlog Generation

```text
- generated_videos table
- FFmpeg pipeline for image slideshow
- music selection by trip mood
- POST /api/v1/trips/{tripId}/vlog
```

---

# 21. Final summary

The correct production-style flow for Task 1 is:

```text
Authenticated request
    ↓
Input validation (dates, traveler count, budget type)
    ↓
Trip duration calculation
    ↓
Destination resolution (alias lookup → DB record)
    ↓
Destination knowledge loaded from DB (DestinationContext)
    ↓
Transport template loaded from DB
    ↓
Cost rules loaded from DB → preliminary BudgetEstimate
    ↓
AI prompt built with full JSON schema inline
    ↓
AI call → structured JSON response
    ↓
AI JSON validated (day count, required fields)
    ↓
Final BudgetEstimate recalculated from DB rules
    ↓
All data persisted in one transaction (trip + days + items + budget + meal plan + accommodation)
    ↓
ApiResponse<TripDetailResponse> returned to Android
```

This makes the project easier to debug, easier to expand, more reliable as a product, and demonstrates real production engineering skill.