# TravelWiki — Grounded Itinerary Generation Architecture (RAG + Deterministic Core)

This document specifies the target architecture for itinerary generation. It is a companion to
`task1_backend_spring_boot_guideline.md` (design intent) and `implementation_progress.md` (current
reality). It explains **why** the system is shaped this way, not just what to build.

It is written to be implementable incrementally on top of the existing Spring Boot backend without
discarding any of the auth or budget work already done.

---

## 0. TL;DR

A pure database makes you trustworthy but you can never cover the whole world.
A pure LLM covers the world but lies about facts and never gives the same answer twice.

The system resolves this with three moves layered on top of Retrieval-Augmented Generation:

1. **Trust-tiered knowledge.** Curated DB > knowledge corpus (vector) > live external APIs > LLM
   memory. Higher tiers win conflicts. The budget engine reads only the top tier.
2. **Fact / narrative split.** The LLM owns prose (descriptions, daily flow, meal ideas, tips).
   The backend owns every number that matters (cost, duration, route choice, dates). The LLM is
   never the source of truth for a figure a user will act on.
3. **Progressive enrichment.** Unknown destinations fall through from curated → corpus → async
   ingestion. Popular destinations get promoted back up to the curated tier over time, so the
   system becomes more trustworthy the more it is used.

Determinism comes from the fact/narrative split plus an **itinerary cache** keyed on normalized
inputs: the same request returns the same saved plan instead of regenerating.

---

## 1. The real problem

The task is usually framed as "DB vs LLM". That framing hides the actual constraint. We are trying
to satisfy three properties at once:

| Property | What it means | Pure DB | Pure LLM | Naive RAG |
|---|---|---|---|---|
| **Coverage** | Works for any destination, not a curated few | ❌ | ✅ | ⚠️ (only what is indexed) |
| **Trustworthiness** | Facts (routes, prices, logistics) are correct | ✅ | ❌ | ⚠️ (still hallucinates) |
| **Determinism** | Same input → same output (esp. cost) | ✅ | ❌ | ❌ |

No single approach gives all three. RAG is necessary but **not sufficient** — naive RAG still
hallucinates around the retrieved context and still emits non-deterministic numbers. The
architecture below adds the missing pieces.

### The two design principles that make it work

**Principle A — Trust is tiered, not binary.**
A curated `cost_rules` row is not equally trustworthy to a web snippet. Encode the hierarchy
explicitly and let higher tiers override lower ones during context assembly and conflict resolution.

**Principle B — Never let the LLM own a number you care about.**
This is already the spirit of the existing guideline ("recalculate budget from rules; never trust AI
totals"). Generalize it: classify every output field as a **fact** (deterministic, backend-owned,
must be cited to a trusted tier) or **narrative** (LLM-owned, free prose). The LLM may *mention*
costs as display hints, but the persisted/returned figures always come from the deterministic engine.

This single split is the answer to "the LLM gives different prices every time." We stop asking the
LLM for prices.

---

## 2. Trust-tiered knowledge model

Four knowledge tiers, ranked by trust. Retrieval pulls from all available tiers; conflict resolution
and the budget engine respect the ranking.

```
Tier 1  CURATED STRUCTURED DB        highest trust, deterministic, machine-readable
        destinations, destination_activities, transport_templates, cost_rules
        → SOLE source for the budget engine. Hand/AI-curated, reviewed.

Tier 2  KNOWLEDGE CORPUS (vector)     semantic, broad coverage, descriptive
        chunked + embedded travel guides, ingested destination articles, prior trips
        → grounds narrative for the long tail of destinations.

Tier 3  LIVE EXTERNAL APIS            fresh, bounded, real-world
        weather, places/geocoding (later), transport feeds (later)
        → freshness for volatile facts; never blocks the core flow in the MVP.

Tier 4  LLM PARAMETRIC MEMORY         lowest trust, fallback only
        → used only to phrase narrative; never cited as a fact, never feeds the budget.
```

Rule: **a field's trust ceiling is the lowest tier that contributed to it.** A cost can only be
"trusted" if it came from Tier 1. A description grounded only in Tier 4 is flagged low-confidence.

---

## 3. High-level architecture (layered, ports-and-adapters)

```
                          ┌─────────────────────────────────────┐
   Android client ──────▶ │           API / Controller          │  POST /api/v1/trips/generate
                          │  (auth context, request validation) │  GET/DELETE /api/v1/trips/...
                          └───────────────────┬─────────────────┘
                                              │ GenerateTripRequest + userId
                                              ▼
                          ┌─────────────────────────────────────┐
                          │      Itinerary Orchestrator         │  thin coordinator, owns the
                          │      (TripGenerationService)        │  @Transactional boundary only
                          └───┬───────┬───────┬───────┬─────────┘
            ┌─────────────────┘       │       │       └─────────────────────┐
            ▼                         ▼       ▼                             ▼
 ┌────────────────────┐  ┌────────────────────────┐  ┌───────────────┐  ┌──────────────────┐
 │ Query Understanding│  │ Retrieval Orchestrator  │  │ Budget Engine │  │  Itinerary Cache │
 │ - destination      │  │ (fan-out + fuse + rerank│  │ DETERMINISTIC │  │  idempotency by  │
 │   resolution       │  │  over the tiers below)  │  │ Tier-1 only   │  │  normalized key  │
 │ - intent/prefs     │  └───────────┬─────────────┘  └───────┬───────┘  └────────┬─────────┘
 │ - duration calc    │              │                        │                   │
 └────────────────────┘   ┌──────────┼──────────┬─────────┐   │                   │
                          ▼          ▼          ▼         ▼   │                   │
                   ┌──────────┐┌──────────┐┌─────────┐┌──────────┐                │
                   │Structured││ Vector   ││ External││  Web /   │  Retriever      │
                   │ Retriever││ Retriever││ API     ││ Ingest   │  port + adapters│
                   │ (Tier 1) ││ (Tier 2) ││(Tier 3) ││ (Tier 2*)│                 │
                   └──────────┘└──────────┘└─────────┘└──────────┘                 │
                                              │                                    │
                                              ▼                                    │
                          ┌─────────────────────────────────────┐                 │
                          │     Grounded Context Assembler      │  builds the      │
                          │  (tier-ordered, cited, token-bound) │  prompt context  │
                          └───────────────────┬─────────────────┘                 │
                                              ▼                                    │
                          ┌─────────────────────────────────────┐                 │
                          │      Itinerary Generator (LLM)      │  narrative only, │
                          │   AiClient behind a port; low temp  │  structured JSON │
                          └───────────────────┬─────────────────┘                 │
                                              ▼                                    │
                          ┌─────────────────────────────────────┐                 │
                          │     Validation + Grounding Gate     │  structure +     │
                          │  (structural + factual groundedness)│  citation check  │
                          └───────────────────┬─────────────────┘                 │
                                              ▼                                    │
                          ┌─────────────────────────────────────┐                 │
                          │   Assembly: narrative + Tier-1 facts│ ◀───────────────┘
                          │   + recomputed budget → persist     │
                          └───────────────────┬─────────────────┘
                                              ▼
                          ┌─────────────────────────────────────┐
                          │  Persistence (single @Transactional)│  trip + days + items +
                          │  + cache write                      │  budget + meals + accommodation
                          └─────────────────────────────────────┘

   Async, off the request path:
   ┌─────────────────────────────────────────────────────────────────────────────┐
   │  Ingestion / Enrichment Pipeline (queue-driven)                              │
   │  fetch → clean → chunk → embed → upsert corpus → (later) promote to Tier 1   │
   └─────────────────────────────────────────────────────────────────────────────┘
```

The orchestrator is deliberately **thin**: it sequences ports and owns the transaction. All real
work lives in single-responsibility components behind interfaces.

---

## 4. The generation pipeline, stage by stage

### Stage 1 — Query understanding
Inputs: `GenerateTripRequest` + authenticated `userId`.
- **Destination resolution.** Normalize free text and resolve against `destination_aliases`
  (Tier 1). Three outcomes drive the rest of the flow:
  - `CURATED` — alias matched a curated destination (Saint Martin today).
  - `CORPUS` — no curated match, but the corpus has indexed content (Tier 2).
  - `COLD` — nothing indexed; trigger async ingestion, serve a best-effort grounded plan with a
    low-confidence caveat.
- **Intent / preferences.** Parse `preferences`, `budgetType`, `specialNotes` into retrieval filters.
- **Duration.** `tripDays` / `tripNights` computed in code (already specified in the guideline).

Replaces the hard failure "throw `DestinationNotSupportedException` if no alias matches" with a
graceful fall-through. Curated destinations still get the strongest path.

### Stage 2 — Hybrid retrieval (fan-out)
Run retrievers **in parallel**, each behind the same `KnowledgeRetriever` port:
- **Structured retriever (Tier 1):** loads `DestinationContext`, transport templates, cost rules.
- **Vector retriever (Tier 2):** semantic search over the embedded corpus. Use **hybrid search** —
  dense vectors *and* keyword/full-text — because pure vector search misses exact names
  ("Teknaf", "Coral Beach") and pure keyword misses paraphrase.
- **External API retriever (Tier 3):** weather/places where relevant (optional in MVP).

Each result carries its **tier**, a **source id**, and a **score**, so later stages can rank,
cite, and resolve conflicts.

### Stage 3 — Fusion + rerank
- Merge ranked lists with **Reciprocal Rank Fusion (RRF)** — robust, parameter-light, no score
  normalization headaches.
- **Rerank** the fused top-N to a small top-K that fits the prompt budget. MVP: skip or use an
  LLM-as-reranker. Later: a cross-encoder reranker. The reranker sits behind its own port so it can
  be added without touching the orchestrator.

Retrieving broadly then reranking narrowly is what keeps the limited context window full of the
*most relevant* facts rather than the *most numerous* ones.

### Stage 4 — Grounded context assembly
Build the prompt context, **ordered by tier (highest first)**, each chunk tagged with a citation id.
- Token-bounded; drop lowest-tier chunks first when over budget.
- Conflicts resolved by tier: if Tier 1 and Tier 2 disagree, Tier 1 wins and the Tier 2 chunk is
  dropped or down-weighted.
- The assembled context is what the LLM is *allowed* to use. The prompt instructs: **use only the
  provided context; do not invent facts; cite the context id for any factual claim.**

### Stage 5 — Constrained generation (narrative only)
- The existing OpenAI integration (`AiClient` + `AiItineraryService`) with
  `response_format: json_object` and the inline schema from the guideline.
- **Low temperature** (≈0.2) for the factual scaffold; the prose can tolerate it because the
  schema is fixed.
- The schema's `estimatedCost` fields are explicitly **display hints only** — documented as
  non-authoritative. The LLM organizes days, writes summaries, suggests meals and tips.

### Stage 6 — Validation + grounding gate
Two checks; both must pass or the response is rejected (with one retry, then a clean error):
- **Structural** (already specified): non-empty title, day count matches `tripDays`, every day has
  items, etc.
- **Grounding** (new): factual claims should trace to retrieved context. MVP can be heuristic
  (key entities/routes appear in the context); later, an LLM groundedness scorer. Output a
  **confidence score**; below threshold → regenerate or attach a "low-confidence, unverified
  destination" flag for `COLD` destinations.

Never persist an ungrounded or partial itinerary.

### Stage 7 — Deterministic post-processing (the trust anchor)
- **Recompute the budget** from Tier-1 `cost_rules` via `BudgetEstimationService` — unchanged from
  the guideline. This is the single source of truth for every figure.
- For `CORPUS`/`COLD` destinations with no Tier-1 cost rules yet, use a **rule-based fallback
  estimator** (regional/budget-type defaults) and clearly mark the budget as an estimate. The point
  stands: the number is computed by code, never lifted from the LLM.
- Discard all LLM-emitted totals.

### Stage 8 — Final assembly, persistence, cache
- Merge LLM narrative + Tier-1 facts + recomputed budget into `TripDetailResponse`.
- Persist trip, days, items, budget, meals, accommodation in **one `@Transactional`** boundary
  (already specified).
- **Write to the itinerary cache** keyed on the normalized request (Stage 9).

---

## 5. How each original problem is solved

### Problem: coverage (can't curate the world)
**Progressive enrichment.** Three paths by resolution outcome:
- `CURATED` → strongest path, full Tier-1 facts + budget.
- `CORPUS` → RAG over indexed content; budget from regional fallback rules.
- `COLD` → enqueue an ingestion job (fetch → clean → chunk → embed → upsert corpus); serve a
  best-effort plan now with a confidence caveat; next request for that destination hits `CORPUS`.

Over time, destinations that get heavy traffic are **promoted** from corpus to curated Tier 1
(cost rules + transport templates authored, AI-assisted then human-reviewed). The knowledge base
grows toward the demand curve instead of trying to cover everything up front. Coverage scales with
usage, not with manual effort.

### Problem: hallucination
**Grounded generation + the grounding gate.** The LLM may only use assembled context, must cite
facts, and the gate rejects responses whose factual claims don't trace to retrieved context. Tier
ordering means the most trustworthy facts dominate the context window.

### Problem: non-determinism (different prices each time)
Two mechanisms:
- **Fact/narrative split** — costs, durations, route selection, and dates are computed/selected by
  code from Tier 1, so they are identical for identical inputs regardless of LLM variance.
- **Itinerary cache** — keyed on `(destinationCode, startDate, endDate, travelerCount, budgetType,
  hash(preferences), corpusVersion)`. Same request → return the saved plan. Regenerate only on cache
  miss or when the underlying corpus/curation version changes. This makes the user-facing response
  fully idempotent and also cuts cost and latency.

---

## 6. Decoupling: the ports

Each is a Java interface with swappable adapters. Nothing downstream knows which concrete source or
model is in use.

```
KnowledgeRetriever          retrieve(RetrievalQuery) -> List<RetrievedChunk>
  ├─ StructuredRetriever        (Tier 1, Postgres)
  ├─ VectorRetriever            (Tier 2, pgvector → swappable to Qdrant/Weaviate)
  ├─ ExternalApiRetriever       (Tier 3, weather/places)
  └─ WebRetriever               (Tier 2*, feeds ingestion)

RetrievalOrchestrator       fan-out, RRF fuse, hand to Reranker
Reranker                    rerank(query, chunks) -> chunks         (no-op adapter for MVP)
ContextAssembler            assemble(chunks, budget) -> GroundedContext
ItineraryGenerator          generate(prompt) -> AiItineraryResponse  (wraps existing AiClient)
GroundingValidator          validate(response, context) -> GroundingResult
BudgetEngine                estimate(...) -> BudgetEstimate          (NEVER imports AI types)
ItineraryCache              get/put by normalized key
IngestionPipeline           enqueue(destinationRef)                  (async)
EmbeddingProvider           embed(text) -> float[]                   (swappable model)
```

Decoupling payoffs: swap OpenAI for another LLM, swap pgvector for a dedicated vector DB, add a new
retriever or a reranker — all without changing the orchestrator. The budget engine stays a pure,
unit-testable function with no AI dependency, which is exactly why it can be the trust anchor.

---

## 7. Data stores

| Store | Holds | Tier | Notes |
|---|---|---|---|
| PostgreSQL (relational) | users, trips, itinerary, **cost_rules, transport_templates, destinations** | 1 | source of truth for facts + persistence |
| pgvector (in Postgres) | embedded corpus chunks + metadata | 2 | one DB to start; behind `VectorRetriever` so it can move out |
| Cache (in-proc → Redis) | rendered itineraries, embeddings, hot retrievals | — | idempotency + cost/latency control |
| Object storage (later) | source docs for ingestion, media | — | S3/Cloudinary per guideline |
| Queue (later) | ingestion + async generation jobs | — | decouples slow work from requests |

Start with everything inside Postgres (relational + pgvector) and an in-process cache. Promote to
Redis and a dedicated vector DB only when load demands it — the ports make that a config change, not
a rewrite.

---

## 8. Scalability

- **Generation is the bottleneck** (latency, token cost, provider rate limits). Mitigate with the
  cache (most hot routes served without an LLM call) and **async generation**: return `202` with a
  `tripId` in `GENERATING` status (the `TripStatus` enum already exists), let the client poll
  `GET /trips/{id}`. Heavy generation moves to a worker pool / queue.
- **Retrieval** scales with the corpus. pgvector is fine to low-millions of chunks; beyond that,
  move `VectorRetriever` to Qdrant/Weaviate/Pinecone behind the same port.
- **Ingestion** is fully async and off the request path; spikes queue up instead of degrading user
  requests.
- **App servers are stateless** (JWT auth, no session) → horizontal scaling is free; cache and DB
  are the shared state.
- **Cost control**: cache aggressively, rerank to keep prompts small, use a small model
  (gpt-4o-mini per guideline) for narrative, reserve larger models only for low-confidence retries.

---

## 9. Failure modes and fallbacks

| Failure | Behavior |
|---|---|
| Vector store down | degrade to Tier-1-only for curated destinations; `COLD`/`CORPUS` get a clean "temporarily unavailable" error |
| LLM provider error/timeout | `AiProviderException` → retry once → clean `502`; nothing persisted |
| LLM returns malformed/partial JSON | retry once at temp 0 with stricter schema; then `AiResponseValidationException`; nothing persisted |
| Grounding score below threshold | regenerate once; if still low and destination is `COLD`, attach low-confidence flag rather than fail |
| No Tier-1 cost rules for destination | rule-based fallback estimator, budget marked as estimate |
| Cache miss under load | async generation path; client polls |

Invariant: **a failed or ungrounded itinerary is never written to the database.**

---

## 10. Mapping onto the existing Spring Boot structure

Reuses the planned packages; adds RAG-specific ones. Nothing in `auth`, `security`, `config`, or the
budget rules changes.

```
com.example.travelwiki
├── trip/service/TripGenerationService     thin orchestrator (Stages 1–8 sequencing)
├── trip/service/TripPersistenceService    unchanged (single @Transactional)
├── trip/service/TripQueryService          unchanged (ownership enforced)
│
├── retrieval/                              NEW — the RAG core
│   ├── KnowledgeRetriever (port)
│   ├── RetrievalOrchestrator
│   ├── Reranker (port + no-op adapter)
│   ├── ContextAssembler
│   ├── dto/ (RetrievalQuery, RetrievedChunk, GroundedContext)
│   └── adapter/
│       ├── StructuredRetriever
│       ├── VectorRetriever
│       ├── ExternalApiRetriever
│       └── WebRetriever
│
├── corpus/                                 NEW — Tier 2 store + embeddings
│   ├── entity/CorpusChunk.java
│   ├── repository/CorpusChunkRepository.java   (pgvector queries)
│   └── service/EmbeddingProvider (port) + OpenAiEmbeddingAdapter
│
├── ingestion/                              NEW — async enrichment (Stage: progressive coverage)
│   ├── IngestionPipeline (port)
│   ├── service/ (fetch, clean, chunk, embed, upsert, promote)
│   └── job/ (queue consumers — later)
│
├── itinerary/
│   ├── ai/AiItineraryService               wraps ItineraryGenerator port
│   ├── prompt/ItineraryPromptBuilder       now consumes GroundedContext
│   ├── validator/AiItineraryValidator      structural (exists)
│   └── validator/GroundingValidator        NEW — grounding gate
│
├── budget/service/BudgetEstimationService  unchanged — the trust anchor (no AI imports)
├── ai/client/AiClient                      unchanged — HTTP only
└── common/
    ├── response/ApiResponse<T>             per guideline
    └── cache/ItineraryCache (port)         NEW — idempotency
```

---

## 11. Phased rollout (you do not build all of this at once)

The architecture is designed so the MVP is a strict subset and each phase adds one capability behind
a port that already exists.

**Phase 0 — MVP, curated-only (closest to current guideline).**
Build the orchestrator, `StructuredRetriever` (Tier 1 only), prompt with the curated context,
generation, structural validation, deterministic budget, persistence, and the itinerary cache.
Saint Martin works end-to-end. `ContextAssembler` exists but only ever sees Tier 1.
*This is shippable and already more correct than naive LLM because of the fact/narrative split + cache.*

**Phase 1 — Add the corpus (Tier 2 + hybrid retrieval).**
Stand up pgvector, `EmbeddingProvider`, `VectorRetriever`, RRF fusion. Seed the corpus with a handful
of destination guides. `CORPUS` destinations now work. Add the grounding gate.

**Phase 2 — Progressive enrichment.**
`WebRetriever` + async `IngestionPipeline`. `COLD` destinations self-heal. Add the promotion path
(corpus → curated, AI-assisted authoring of cost rules + transport templates with human review).

**Phase 3 — Freshness + scale.**
`ExternalApiRetriever` (weather/places, Tier 3). Reranker adapter. Redis cache. Async generation
with status polling. Move vector store out of Postgres if the corpus outgrows it.

Each phase is independently valuable and reversible, because every new piece plugs into a port the
earlier phase already defined.

---

## 12. Why this is the right shape

- **Trustworthy** because numbers never come from the LLM and facts are tier-ranked and cited.
- **Covers the world** because unknown destinations fall through to retrieval and the corpus grows
  with demand instead of with manual curation.
- **Deterministic** because facts are computed in code and identical requests are served from cache.
- **Scalable** because the slow, expensive parts (generation, ingestion) are cacheable and async,
  and app servers are stateless.
- **Maintainable / decoupled** because every source, the model, the reranker, and the cache sit
  behind ports; the trust anchor (budget engine) is a pure function with zero AI coupling.

It also preserves everything already built — auth, security, the budget rules, the persistence
model — and treats the existing "recompute the budget, never trust AI totals" rule as the seed of the
whole trust model rather than a one-off safeguard.
