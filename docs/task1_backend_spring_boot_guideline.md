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
- Flyway or Liquibase for database migration
- Jakarta Bean Validation
- Lombok, optional
- MapStruct, optional
- Docker

## Later additions

- Redis for caching destination/weather/place data
- pgvector for image/text semantic search
- Cloudinary, S3, or S3-compatible storage for media
- Firebase Cloud Messaging for weather notifications
- FFmpeg service for vlog generation

---

# 3. Global Spring Boot folder structure

Recommended root package:

```text
com.yourname.travelplanner
```

Recommended folder structure:

```text
src/main/java/com/yourname/travelplanner
│
├── TravelPlannerApplication.java
│
├── auth
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── security
│
├── user
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── trip
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── mapper
│
├── itinerary
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
├── destination
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── seed
│
├── transport
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── budget
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── rules
│
├── ai
│   ├── client
│   ├── dto
│   ├── config
│   └── exception
│
├── weather
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── places
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── media
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── storage
│
├── blog
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── notification
│   ├── dto
│   └── service
│
├── common
│   ├── config
│   ├── dto
│   ├── enums
│   ├── exception
│   ├── response
│   ├── util
│   └── validation
│
└── infra
    ├── database
    ├── external
    └── logging
```

---

# 4. Why this folder structure is useful

## `auth`

Handles authentication and authorization.

Responsibilities:

- register user
- login user
- generate JWT
- validate JWT
- secure API endpoints

## `user`

Handles user profile information.

Responsibilities:

- user profile
- user preferences
- user settings

## `trip`

Handles the main trip object.

Responsibilities:

- create trip
- list user trips
- get trip details
- delete trip
- update trip metadata

## `itinerary`

Handles itinerary generation and storage.

Responsibilities:

- generate itinerary
- parse AI response
- save itinerary days
- save itinerary items
- return mobile-friendly itinerary response

## `destination`

Stores and retrieves destination knowledge.

Responsibilities:

- destination profile
- popular activities
- local tips
- recommended duration
- destination-specific cost hints

Example destinations:

- Saint Martin
- Cox's Bazar
- Sylhet
- Sajek
- Bandarban
- Kuakata

## `transport`

Stores transport templates and travel route options.

Responsibilities:

- source-to-destination route templates
- estimated travel time
- estimated transport cost
- budget/mid-range/luxury transport choices

## `budget`

Handles cost calculation.

Responsibilities:

- food cost
- transport cost
- accommodation cost
- activity cost
- miscellaneous cost
- total cost
- per-person cost

## `ai`

Handles direct communication with AI APIs.

Responsibilities:

- call AI model
- send prompt
- receive response
- handle timeout/retry/error

Important rule:

> Keep AI API calling logic separate from itinerary business logic.

## `common`

Stores shared utilities, exceptions, response wrappers, and enums.

Responsibilities:

- global error handling
- API response format
- custom exceptions
- shared enums
- date utilities
- validation helpers

---

# 5. Task 1 backend scope

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

# 6. Task 1 backend flow in detail

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

Recommended endpoint:

```http
POST /api/v1/trips/generate
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

The endpoint should create a trip and generate the itinerary in one flow for the MVP.

Later, this can be split into:

```http
POST /api/v1/trips
POST /api/v1/trips/{tripId}/generate-itinerary
```

---

## Step 3: Backend validates input

Use Jakarta Bean Validation annotations on the request DTO.

Example DTO:

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

Additional service-level validation:

```text
- endDate must not be before startDate
- trip duration must be at least 1 day
- trip duration should not exceed allowed limit, for example 30 days
- destination must be supported or at least searchable
- traveler count must be reasonable
- budget type must be valid
```

Recommended custom validator method:

```java
public void validateGenerateTripRequest(GenerateTripRequest request) {
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

Formula:

```text
tripDays = days between startDate and endDate + 1
tripNights = max(tripDays - 1, 0)
```

Example:

```text
Start date: 2026-06-10
End date: 2026-06-12

Trip days = 3
Trip nights = 2
```

Java example:

```java
long tripDays = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
long tripNights = Math.max(tripDays - 1, 0);
```

---

## Step 5: Backend identifies destination

The backend should normalize the user destination input.

Example:

```text
"saint martin"
"Saint Martin"
"St. Martin"
"Saint Martin Island"
```

All should map to one destination record:

```text
SAINT_MARTIN
```

Recommended approaches:

### MVP approach

Use simple database aliases.

Example table:

```text
destination_aliases
- saint martin
- st martin
- saint martin island
- st. martin island
```

### Later advanced approach

Use geocoding or places API to resolve destinations.

For the MVP, use the database approach.

---

## Step 6: Backend loads destination knowledge from DB

Destination knowledge is the curated information your system knows about a destination.

Example destination knowledge for Saint Martin:

```text
Destination: Saint Martin
Type: Island / Beach
Recommended duration: 2-4 days
Best for: beach, seafood, photography, relaxation
Popular activities:
- beach walk
- sunrise viewing
- sunset viewing
- seafood dinner
- cycling
- local market visit
- coral beach visit
Local tips:
- Ship schedules may depend on weather and season
- Keep buffer time for transport
- Carry cash because digital payment may not always be available
```

Recommended table:

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Recommended activities table:

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

Recommended alias table:

```sql
CREATE TABLE destination_aliases (
    id BIGSERIAL PRIMARY KEY,
    destination_id BIGINT NOT NULL REFERENCES destinations(id),
    alias VARCHAR(150) NOT NULL UNIQUE
);
```

---

## Step 7: Backend loads transport templates

Transport templates describe how a user can reach a destination from a starting point.

Example for Dhaka to Saint Martin:

```text
Budget:
Dhaka → Cox's Bazar/Teknaf by bus → Teknaf → Saint Martin by ship

Mid-range:
Dhaka → Cox's Bazar by AC bus or flight → Teknaf by road → Saint Martin by ship

Luxury:
Dhaka → Cox's Bazar by flight → private transfer to Teknaf → Saint Martin by ship
```

Recommended table:

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

Example row:

```text
from_location: Dhaka
to_destination: Saint Martin
budget_type: MID_RANGE
title: AC bus or flight assisted route
route_summary: Travel from Dhaka to Cox's Bazar or Teknaf, continue by road to Teknaf, then take a ship to Saint Martin.
estimated_time_min_minutes: 720
estimated_time_max_minutes: 960
cost_min_per_person: 4000
cost_max_per_person: 7000
```

---

## Step 8: Backend loads cost rules based on budget type

The backend should calculate budget using rules.

Do not rely only on AI for the final total.

Recommended enum:

```java
public enum BudgetType {
    BUDGET,
    MID_RANGE,
    LUXURY
}
```

Recommended cost rule table:

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

Budget calculation formulas:

```text
roomsNeeded = ceil(travelerCount / 2.0)

foodCost = selectedFoodCostPerPersonPerDay * travelerCount * tripDays

accommodationCost = selectedRoomCostPerNight * roomsNeeded * tripNights

transportCost = selectedTransportCostPerPerson * travelerCount

activityCost = selectedActivityCostPerPersonPerDay * travelerCount * tripDays

miscCost = selectedMiscCostPerPersonPerDay * travelerCount * tripDays

subtotal = foodCost + accommodationCost + transportCost + activityCost + miscCost

buffer = subtotal * 0.10

totalCost = subtotal + buffer

perPersonCost = totalCost / travelerCount
```

For MVP, use midpoint of min and max:

```text
selectedCost = (min + max) / 2
```

Later, you can allow the user to choose lower/average/higher estimate.

---

## Step 9: Backend prepares AI prompt

The prompt should be generated by the backend using:

- user request
- destination knowledge
- transport templates
- cost rules
- budget estimate
- required output JSON schema

Recommended prompt builder package:

```text
itinerary/prompt
```

Recommended class:

```java
ItineraryPromptBuilder
```

Prompt builder input:

```java
public record ItineraryPromptContext(
    GenerateTripRequest request,
    Destination destination,
    List<DestinationActivity> activities,
    TransportTemplate transportTemplate,
    BudgetEstimate preliminaryBudget,
    long tripDays,
    long tripNights
) {}
```

Example prompt instruction:

```text
You are a travel itinerary planning assistant.

Create a practical travel itinerary using only the provided context.
Do not invent exact hotel names unless provided.
Do not invent exact live ticket prices.
Use the provided cost estimate as the budget boundary.
Return only valid JSON.

User request:
- Destination: Saint Martin
- Starting location: Dhaka
- Trip days: 3
- Trip nights: 2
- Travelers: 3
- Budget type: MID_RANGE
- Preferences: beach, seafood, photography, relaxing

Destination context:
- Type: Island / Beach
- Best for: beach, seafood, photography, relaxation
- Local tips: Ship schedule can depend on season and weather. Keep buffer time.

Transport context:
- Route: AC bus or flight to Cox's Bazar, road transfer to Teknaf, ship to Saint Martin
- Estimated time: 12-16 hours
- Estimated transport cost per person: 4000-7000 BDT

Budget estimate:
- Transport: 15000 BDT
- Food: 9000 BDT
- Accommodation: 12000 BDT
- Activities: 4500 BDT
- Misc: 3000 BDT
- Estimated total: 43500 BDT

Return JSON using the required schema.
```

---

## Step 10: AI generates structured itinerary JSON

The AI should return JSON only.

Recommended AI output schema:

```json
{
  "title": "string",
  "summary": "string",
  "destination": "string",
  "startLocation": "string",
  "tripDays": 3,
  "tripNights": 2,
  "travelerCount": 3,
  "budgetType": "MID_RANGE",
  "transportPlan": {
    "title": "string",
    "summary": "string",
    "estimatedTime": "string",
    "notes": ["string"]
  },
  "days": [
    {
      "dayNumber": 1,
      "title": "string",
      "summary": "string",
      "items": [
        {
          "time": "08:00",
          "category": "TRANSPORT",
          "title": "string",
          "description": "string",
          "locationName": "string",
          "estimatedCost": 0,
          "durationMinutes": 60
        }
      ]
    }
  ],
  "mealPlan": [
    {
      "dayNumber": 1,
      "breakfast": "string",
      "lunch": "string",
      "dinner": "string"
    }
  ],
  "accommodationSuggestion": {
    "type": "MID_RANGE_HOTEL_OR_RESORT",
    "description": "string",
    "estimatedCostPerRoomPerNight": 3500
  },
  "tips": ["string"]
}
```

Important:

The AI may include item-level estimated costs, but the backend should still recalculate the final budget separately.

---

## Step 11: Backend validates AI JSON

The backend should check:

```text
- JSON is valid
- title exists
- summary exists
- days array exists
- number of days matches tripDays
- each day has dayNumber
- each day has items
- item category is valid
- item title is not blank
- meal plan day count matches tripDays, if meal plan exists
- total content is not empty
```

Recommended package:

```text
itinerary/validator
```

Recommended class:

```java
AiItineraryValidator
```

Example validation:

```java
public void validate(AiItineraryResponse response, long expectedTripDays) {
    if (response == null) {
        throw new AiResponseValidationException("AI itinerary response is null");
    }

    if (response.days() == null || response.days().isEmpty()) {
        throw new AiResponseValidationException("AI itinerary contains no days");
    }

    if (response.days().size() != expectedTripDays) {
        throw new AiResponseValidationException("AI itinerary day count does not match trip duration");
    }
}
```

Possible fallback strategy:

```text
If AI response is invalid:
1. Retry once with a stricter repair prompt
2. If still invalid, return a clear backend error
3. Do not save broken itinerary
```

---

## Step 12: Backend recalculates cost safely

This is very important.

Even if AI returns costs, the backend should calculate final cost again.

Reason:

```text
AI may hallucinate inconsistent prices.
AI may forget traveler count.
AI may calculate wrong totals.
AI may return costs that do not match the selected budget type.
```

Recommended service:

```java
BudgetEstimationService
```

Recommended output DTO:

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

## Step 13: Backend saves trip, days, items, and budget

Recommended tables:

### trips

```sql
CREATE TABLE trips (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### itinerary_days

```sql
CREATE TABLE itinerary_days (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    day_number INT NOT NULL,
    title VARCHAR(200),
    summary TEXT,
    date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

Recommended response format:

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

The backend should return mobile-friendly data.

The Android app should not need to parse long text into sections.

Good backend response design allows the app to show:

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

# 7. Recommended API endpoints for Task 1

## Generate trip itinerary

```http
POST /api/v1/trips/generate
```

Purpose:

```text
Create a trip and generate a complete itinerary in one request.
```

Request:

```json
{
  "destination": "Saint Martin",
  "startLocation": "Dhaka",
  "startDate": "2026-06-10",
  "endDate": "2026-06-12",
  "travelerCount": 3,
  "budgetType": "MID_RANGE",
  "preferences": ["beach", "seafood", "photography"],
  "specialNotes": "Relaxed post-exam trip"
}
```

Response:

```json
{
  "success": true,
  "message": "Trip itinerary generated successfully",
  "data": {}
}
```

## Get all trips of logged-in user

```http
GET /api/v1/trips
```

## Get single trip details

```http
GET /api/v1/trips/{tripId}
```

## Delete trip

```http
DELETE /api/v1/trips/{tripId}
```

---

# 8. Main backend classes for Task 1

## Controller

```text
trip/controller/TripController.java
```

Responsibilities:

- receive request from Android
- call TripGenerationFacade or TripGenerationService
- return response

Suggested methods:

```java
@PostMapping("/generate")
public ResponseEntity<ApiResponse<TripDetailResponse>> generateTrip(
    @Valid @RequestBody GenerateTripRequest request,
    Authentication authentication
) {
    TripDetailResponse response = tripGenerationService.generateTrip(request, authentication);
    return ResponseEntity.ok(ApiResponse.success("Trip itinerary generated successfully", response));
}
```

---

## Service orchestration

```text
trip/service/TripGenerationService.java
```

This service should control the full flow.

Responsibilities:

- validate request
- calculate duration
- resolve destination
- load destination context
- load transport template
- estimate preliminary budget
- build AI prompt
- call AI service
- validate AI response
- recalculate budget
- save trip and related data
- return final response

Pseudo-code:

```java
@Transactional
public TripDetailResponse generateTrip(GenerateTripRequest request, Authentication authentication) {
    User user = authUserResolver.resolve(authentication);

    tripRequestValidator.validate(request);

    TripDuration duration = tripDurationCalculator.calculate(request.startDate(), request.endDate());

    Destination destination = destinationService.resolveDestination(request.destination());

    DestinationContext destinationContext = destinationService.loadContext(destination.getId());

    TransportTemplate transportTemplate = transportService.findBestTemplate(
        request.startLocation(),
        destination.getId(),
        request.budgetType()
    );

    BudgetEstimate preliminaryBudget = budgetEstimationService.estimate(
        request,
        destination,
        transportTemplate,
        duration
    );

    String prompt = itineraryPromptBuilder.build(
        request,
        destinationContext,
        transportTemplate,
        preliminaryBudget,
        duration
    );

    AiItineraryResponse aiResponse = aiItineraryService.generate(prompt);

    aiItineraryValidator.validate(aiResponse, duration.tripDays());

    BudgetEstimate finalBudget = budgetEstimationService.estimate(
        request,
        destination,
        transportTemplate,
        duration
    );

    Trip trip = tripPersistenceService.saveGeneratedTrip(
        user,
        request,
        destination,
        duration,
        aiResponse,
        finalBudget
    );

    return tripQueryService.getTripDetails(trip.getId(), user.getId());
}
```

---

## Destination service

```text
destination/service/DestinationService.java
```

Responsibilities:

- normalize destination name
- match aliases
- return destination entity
- load destination activities and tips

Example methods:

```java
Destination resolveDestination(String userInput);
DestinationContext loadContext(Long destinationId);
```

---

## Transport service

```text
transport/service/TransportService.java
```

Responsibilities:

- find transport route from start location to destination
- filter by budget type
- provide estimated time and cost range

Example method:

```java
TransportTemplate findBestTemplate(String startLocation, Long destinationId, BudgetType budgetType);
```

---

## Budget service

```text
budget/service/BudgetEstimationService.java
```

Responsibilities:

- load cost rules
- calculate food cost
- calculate accommodation cost
- calculate transport cost
- calculate activity cost
- calculate misc cost
- calculate buffer
- calculate total and per-person cost

Example method:

```java
BudgetEstimate estimate(
    GenerateTripRequest request,
    Destination destination,
    TransportTemplate transportTemplate,
    TripDuration duration
);
```

---

## Prompt builder

```text
itinerary/prompt/ItineraryPromptBuilder.java
```

Responsibilities:

- convert backend data into AI prompt
- include strict output schema
- include user preferences
- include destination knowledge
- include budget boundaries

---

## AI itinerary service

```text
itinerary/ai/AiItineraryService.java
```

Responsibilities:

- call AI client
- get raw response
- parse JSON into DTO

Example method:

```java
AiItineraryResponse generate(String prompt);
```

---

## AI client

```text
ai/client/AiClient.java
```

Responsibilities:

- direct external AI API communication
- timeout handling
- error handling
- logging minimal metadata

Important:

Do not expose AI API key to the Android app.

---

## AI itinerary validator

```text
itinerary/validator/AiItineraryValidator.java
```

Responsibilities:

- check AI JSON structure
- check day count
- check required fields
- reject invalid output

---

## Persistence service

```text
trip/service/TripPersistenceService.java
```

Responsibilities:

- save trip
- save itinerary days
- save itinerary items
- save budget
- save meal plan
- save accommodation suggestion

---

## Query service

```text
trip/service/TripQueryService.java
```

Responsibilities:

- load saved trip details
- map entities to response DTO
- ensure user can access only their own trips

---

# 9. Important DTOs for Task 1

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

# 10. Important enums

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

# 11. Global API response wrapper

Recommended response format:

```java
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    ErrorResponse error
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> failure(String message, ErrorResponse error) {
        return new ApiResponse<>(false, message, null, error);
    }
}
```

Error response:

```java
public record ErrorResponse(
    String code,
    String message,
    Map<String, String> fieldErrors
) {}
```

---

# 12. Error handling strategy

Create a global exception handler:

```text
common/exception/GlobalExceptionHandler.java
```

Handle:

```text
- validation errors
- bad request errors
- unauthorized errors
- forbidden errors
- not found errors
- AI API errors
- AI response validation errors
- database errors
- unexpected server errors
```

Recommended custom exceptions:

```text
BadRequestException
NotFoundException
UnauthorizedException
ForbiddenException
AiProviderException
AiResponseValidationException
DestinationNotSupportedException
```

Example user-friendly errors:

```json
{
  "success": false,
  "message": "Could not generate itinerary",
  "data": null,
  "error": {
    "code": "AI_RESPONSE_INVALID",
    "message": "The AI response was invalid. Please try again.",
    "fieldErrors": null
  }
}
```

---

# 13. MVP data seeding

For the first version, seed data manually.

Recommended seed destinations:

```text
Saint Martin
Cox's Bazar
Sylhet
Sajek
Bandarban
Kuakata
```

For each destination, seed:

```text
- destination profile
- aliases
- activities
- cost rules by budget type
- transport templates from Dhaka
```

Use Flyway migration files:

```text
src/main/resources/db/migration
├── V1__create_core_tables.sql
├── V2__create_destination_tables.sql
├── V3__create_trip_tables.sql
├── V4__seed_destinations.sql
├── V5__seed_transport_templates.sql
└── V6__seed_cost_rules.sql
```

---

# 14. Application properties structure

Example `application.yml`:

```yaml
spring:
  application:
    name: travel-planner-backend

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

security:
  jwt:
    secret: ${JWT_SECRET}
    expiration-ms: ${JWT_EXPIRATION_MS:86400000}

ai:
  provider: ${AI_PROVIDER:openai}
  api-key: ${AI_API_KEY}
  model: ${AI_MODEL}
  timeout-seconds: 60
```

Never hardcode secrets in source code.

---

# 15. Development order for Task 1

Build in this exact order:

## Stage 1: Project setup

```text
1. Create Spring Boot project
2. Add dependencies
3. Connect PostgreSQL
4. Add Flyway
5. Add global response wrapper
6. Add global exception handler
```

## Stage 2: Auth foundation

```text
1. User entity
2. Register API
3. Login API
4. JWT generation
5. JWT filter
6. Secure trip endpoints
```

## Stage 3: Destination foundation

```text
1. Destination entity
2. DestinationActivity entity
3. DestinationAlias entity
4. Destination repository
5. DestinationService.resolveDestination()
6. Seed Saint Martin data
```

## Stage 4: Transport and cost rules

```text
1. TransportTemplate entity
2. CostRule entity
3. TransportService
4. BudgetEstimationService
5. Unit test budget calculation
```

## Stage 5: Trip and itinerary persistence

```text
1. Trip entity
2. ItineraryDay entity
3. ItineraryItem entity
4. TripBudget entity
5. MealPlan entity
6. AccommodationSuggestion entity
7. Repositories
```

## Stage 6: AI integration

```text
1. AiClient
2. ItineraryPromptBuilder
3. AiItineraryResponse DTO
4. AiItineraryService
5. AiItineraryValidator
```

## Stage 7: Generate trip API

```text
1. GenerateTripRequest DTO
2. TripGenerationService orchestration
3. TripPersistenceService
4. TripQueryService
5. TripController.generateTrip()
```

## Stage 8: Testing and hardening

```text
1. Test invalid dates
2. Test unsupported destination
3. Test budget calculation
4. Test AI invalid JSON fallback
5. Test user cannot access another user's trip
6. Test generated response is Android-friendly
```

---

# 16. Minimum working Task 1 MVP

The first complete backend version should support:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/trips/generate
GET /api/v1/trips
GET /api/v1/trips/{tripId}
DELETE /api/v1/trips/{tripId}
```

The first version should only need one destination to work well:

```text
Saint Martin
```

Once Saint Martin works, add more destinations.

---

# 17. What not to build first

Do not start with:

```text
- maps
- weather notification
- image upload
- semantic image search
- blog generation
- vlog generation
- hotel booking
- payment
- admin dashboard
```

First make Task 1 work end-to-end.

The first success condition:

```text
A logged-in user can create a Saint Martin trip, receive a structured itinerary, receive a backend-calculated budget breakdown, and later reopen the saved trip.
```

---

# 18. Backend responsibility split

## Android app responsibility

```text
- collect trip form data
- call backend API
- store JWT locally
- display loading state
- display generated itinerary
- show errors nicely
```

## Backend responsibility

```text
- authenticate user
- validate trip request
- calculate duration
- resolve destination
- load destination knowledge
- load transport and cost rules
- call AI
- validate AI response
- recalculate budget
- save generated trip
- return structured response
```

## AI responsibility

```text
- generate human-friendly itinerary
- organize daily activities
- create meal suggestions
- produce travel tips
- format according to JSON schema
```

## Database responsibility

```text
- store users
- store destinations
- store transport templates
- store cost rules
- store generated trips
- store itinerary days and items
- store budget breakdown
```

---

# 19. Important engineering principles

## Principle 1: AI output must be structured

Avoid plain text AI output.

Bad:

```text
Here is your trip plan...
```

Good:

```json
{
  "days": [],
  "mealPlan": [],
  "tips": []
}
```

## Principle 2: Backend must recalculate budget

Do not trust AI-generated totals.

## Principle 3: Save generated result

Do not regenerate every time the user opens the trip.

## Principle 4: Keep AI client separate

Do not mix AI API code inside controller.

## Principle 5: Use database seed data first

Do not depend on many external APIs in the first MVP.

## Principle 6: Make response mobile-friendly

The Android app should receive clean arrays and objects, not one huge paragraph.

---

# 20. Future extension path

After Task 1 is complete, extend in this order:

## Task 2: Map Integration

Add:

```text
- latitude/longitude for itinerary items
- place search API
- map pins endpoint
- route visualization support
```

## Task 3: Weather Notifications

Add:

```text
- weather API integration
- weather snapshot table
- weather risk detection
- notification service
```

## Task 4: Trip Blog Generation

Add:

```text
- trip blog table
- blog prompt builder
- blog generation API
- share/export support
```

## Task 5: Image Upload and Textual Search

Add:

```text
- media upload
- album table
- image captioning
- embeddings
- pgvector search
```

## Bonus: Automated Vlog Generation

Add:

```text
- generated video table
- FFmpeg pipeline
- image slideshow generation
- music selection
- export/share
```

---

# 21. Final summary

For Task 1, the backend should not be just:

```text
User input → AI → response
```

The correct production-style flow is:

```text
User input
   ↓
Validation
   ↓
Trip duration calculation
   ↓
Destination resolution
   ↓
Destination knowledge from DB
   ↓
Transport template from DB
   ↓
Cost rules from DB
   ↓
AI prompt generation
   ↓
Structured AI itinerary JSON
   ↓
AI JSON validation
   ↓
Safe backend budget recalculation
   ↓
Database persistence
   ↓
Mobile-friendly API response
```

This makes the project:

- easier to debug
- easier to expand
- more reliable
- more professional
- better for CV
- better for real business use later

