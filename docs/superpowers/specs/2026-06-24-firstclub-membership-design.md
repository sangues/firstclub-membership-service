# FirstClub Membership Program — Design Spec

**Date:** 2026-06-24
**Context:** Backend take-home assignment (SDE4). Java + Spring Boot.
**Evaluation focus (from the prompt):** abstractions, entity design, extensibility, modularity, Java best practices, and a bonus for concurrency thinking. The system must be running, demo-able, and have functional APIs.

---

## 1. Problem summary

Design the backend for a tiered Membership Program. Users subscribe to a paid **plan** (billing cadence), receive a benefit **tier**, and move between tiers both manually and automatically based on their order behavior. The system integrates conceptually with a shopping/checkout journey but does not own checkout.

### In scope
- Membership **plans**: Monthly / Quarterly / Yearly, each with pricing.
- Configurable **benefits** per tier (free delivery, percentage discount, exclusive/early access, priority support).
- **Tiers** (Silver / Gold / Platinum) with both manual upgrade/downgrade and automatic, criteria-driven progression.
- User actions: browse plans/tiers, subscribe, upgrade/downgrade tier, cancel, view current membership + expiry, view effective benefits.
- Lightweight **order-event ingestion** to feed tier-progression criteria.
- Concurrency-safe, idempotent processing.

### Out of scope (explicit non-goals)
- A real checkout/commerce engine. Orders enter only as upstream **events**.
- A real payment gateway (mocked behind a port).
- Authentication / authorization.
- Distributed-system concerns (single-node, in-memory persistence acceptable).

---

## 2. Core design decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Plan ↔ Tier relationship | **Hybrid with explicit decoupling**: a subscription carries a **purchased tier** (billing, user-driven) and an **earned tier** (criteria-driven). **Effective tier = MAX(purchased, earned)** by rank. |
| 2 | Order side | **Lightweight order-event ingestion** — orders are an upstream concern; events feed the criteria engine. |
| 3 | Tier re-evaluation | **Event-driven async (after commit) + scheduled monthly sweep**, guarded by per-user locking + optimistic versioning, idempotent by `orderId`. |
| 4 | Persistence / run | **H2 in-memory + Spring Data JPA**, seeded on startup, one-command run (`./mvnw spring-boot:run`). Postgres swappable via a Spring profile. |
| 5 | Configurability | Benefits and eligibility criteria are **data + strategies, not code** (catalog rows + pluggable handler/rule beans). |
| 6 | Payment | Mocked behind a `PaymentGateway` port with an always-success default impl. |
| 7 | Eligibility | Each tier owns a **collection of `EligibilityRule`s** (type + params), AND-composed and evaluated by per-type strategy beans (Specification / OCP). |
| 8 | Subscription integrity | Explicit **status state machine** (`PENDING → ACTIVE → EXPIRED/CANCELLED`) + **action idempotency** (one active subscription per user + optional `Idempotency-Key`) to prevent double-subscribe / double-bill. |
| 9 | Auditability | Every tier/status change appends an immutable **`TierMovementLog`** record (temporal history for support & analytics). |
| 10 | Scale seams | In-app event bus + scheduled sweep today, behind a `DomainEventPublisher` port and a partitionable sweep — documented swap to Kafka/RabbitMQ + Spring Batch without redesign. |

---

## 3. Domain model

### 3.1 The two axes
- **Plan** = *what you pay for*: billing cadence + price (`MONTHLY` / `QUARTERLY` / `YEARLY`).
- **Tier** = *what you get*: an ordered benefit level (`SILVER` < `GOLD` < `PLATINUM`) that unlocks configurable perks.

A **Subscription** binds a user to one Plan and carries a **current Tier**.

### 3.2 Tier-change reconciliation (the hybrid policy)
The contradiction in the prompt — users both *pay for* a tier and *earn* one — is resolved by storing the two independently and deriving the result:

- **`purchasedTier`** — what the user pays for (set on subscribe; changed by manual upgrade/downgrade). Billing-driven.
- **`earnedTier`** — the highest tier the user qualifies for via order criteria. Criteria-driven, set by the evaluation engine.
- **`effectiveTier` = MAX(purchasedTier, earnedTier)** by rank — the tier whose benefits actually apply. Derived on every change and stored for fast reads.

Behavioral rules:
- **Manual upgrade** raises `purchasedTier` (may require payment); **manual downgrade** lowers it (reduces billing). Because effective is a MAX, a downgrade never strips a status the user has *earned* — it only changes what they pay for. This is the deliberate, defensible product semantic.
- **Earned promotion** raises `earnedTier` immediately when criteria are met.
- **Earned demotion** (criteria lapse) is applied only at the **monthly sweep / renewal boundary**, never mid-cycle — paid benefits are never yanked unexpectedly, and threshold-hovering can't thrash the tier.

> Storing purchased and earned separately (rather than a single `currentTier`) is the key abstraction: it preserves the paid floor, makes "downgrade" well-defined, and removes the ambiguity an SDE4 reviewer will probe.

### 3.3 Configurability — benefits & eligibility as data + strategies

**Benefits**
- **Benefit catalog**: `type` (`FREE_DELIVERY`, `PERCENT_DISCOUNT`, `EXCLUSIVE_DEALS`, `EARLY_ACCESS`, `PRIORITY_SUPPORT`) + typed params (e.g. `discountPercent`, `categories`).
- **TierBenefit mapping**: which benefits a tier unlocks, with tier-specific param values (e.g. Gold = 10% off, Platinum = 15%). Adding a perk = config rows, not code.
- **Benefit strategy registry**: each benefit type has a `BenefitHandler` strategy (e.g. `PercentDiscountHandler`, `FreeDeliveryHandler`) resolved from a registry/factory keyed by the type enum — no `switch`/`if-else`. Handlers resolve a benefit's effective representation today; the same interface is the seam for *applying* a benefit at checkout (checkout itself is out of scope).

**Eligibility (rule engine)**
- A **`Tier` owns a collection of `EligibilityRule`s** (`ruleType` + params), stored as data. A user qualifies for a tier when **all** its rules pass (AND-composition; OR-grouping is a documented future extension).
- Each rule type is an `EligibilityRule` strategy bean — `OrderCountRule`, `OrderValueRule`, `CohortRule` — evaluated against a user's order-stats snapshot. Composing `OrderCountRule AND CohortRule AND SpendRule` is pure config; a brand-new rule type is one new bean, auto-discovered. Strict Open/Closed adherence — the evaluator never changes.
- The engine selects the **highest-rank tier whose rule set the user satisfies** as the `earnedTier`.

### 3.4 Entities (JPA)

| Entity | Purpose / key fields |
|--------|----------------------|
| `MembershipPlan` | `planType`, `price`, `durationDays`, `active` |
| `Tier` | `name`, `rank`, `active` |
| `Benefit` | `type`, `params` (JSON), `description` |
| `TierBenefit` | `tier` ↔ `benefit` (+ param overrides) |
| `EligibilityRule` | `tier`, `ruleType`, `params` (JSON) — one row per rule; a tier has many. Evaluated by the matching strategy bean |
| `User` | `id`, `name`, `cohort` (minimal) |
| `Subscription` | `user`, `plan`, `purchasedTier`, `earnedTier`, `effectiveTier` (derived), `status` (`PENDING/ACTIVE/EXPIRED/CANCELLED`), `startAt`, `expiresAt`, `idempotencyKey`, `@Version` |
| `TierMovementLog` | immutable audit row: `user`, `fromTier`, `toTier`, `kind` (`SUBSCRIBE/UPGRADE/DOWNGRADE/EARNED_PROMOTION/EARNED_DEMOTION/RENEW/CANCEL`), `at` |
| `OrderEvent` | `orderId` (**unique → idempotency**), `user`, `amount`, `occurredAt`, `processed` |
| `UserOrderStats` | per-user aggregate: `totalOrders`, `monthlyOrderValue`, `statsMonth`, `@Version` — materialized from order events, feeds criteria |

---

## 4. API surface (REST)

| Method & path | Purpose |
|---------------|---------|
| `GET /api/plans` | List membership plans + pricing |
| `GET /api/tiers` | List tiers with their unlocked benefits |
| `GET /api/benefits` | Benefit catalog |
| `POST /api/users/{userId}/subscription` | Subscribe `{planType, tier?}` (defaults to base tier) |
| `GET /api/users/{userId}/subscription` | Current membership: plan, tier, source, status, expiry |
| `PATCH /api/users/{userId}/subscription/tier` | Manual upgrade/downgrade `{targetTier}` |
| `DELETE /api/users/{userId}/subscription` | Cancel |
| `GET /api/users/{userId}/benefits` | Effective benefits for the user's **effective** tier |
| `GET /api/users/{userId}/subscription/history` | Tier/status movement history (audit trail) |
| `POST /api/order-events` | Ingest order event `{orderId, userId, amount, occurredAt}` |
| `POST /api/users/{userId}/tier-evaluation` | (admin) force re-eval — useful for the demo |

DTOs are distinct from entities; mapping happens in the service layer. Request bodies are bean-validated (`@Valid`).

`POST .../subscription` and `PATCH .../subscription/tier` accept an optional `Idempotency-Key` header; combined with a one-active-subscription-per-user constraint this prevents double-subscribe / double-bill on a retried or double-clicked request. Subscription responses expose `purchasedTier`, `earnedTier`, and `effectiveTier` explicitly.

---

## 5. Concurrency design (the bonus)

1. **Idempotency** — `OrderEvent.orderId` unique constraint; duplicate ingest is a no-op (catch `DataIntegrityViolationException`). Each order counted once.
2. **Per-user serialization** — order-stats update + tier eval for a given user run under a **striped lock keyed by `userId`** (`ConcurrentHashMap` of locks / Guava `Striped`). Same user serializes; different users run in parallel.
3. **Optimistic locking** — `Subscription` and `UserOrderStats` carry `@Version`; conflicting updates retry on `OptimisticLockException`. Guards manual upgrade racing with auto-promotion.
4. **Async, after commit** — order ingest publishes a domain event via a `DomainEventPublisher` port (in-app `ApplicationEventPublisher` adapter today); a `@TransactionalEventListener(phase = AFTER_COMMIT)` running on a bounded `ThreadPoolTaskExecutor` performs evaluation, so evaluation never reads uncommitted data. Bounded queue with an explicit rejection policy. The port is the seam for a Kafka/RabbitMQ swap — producers and consumers stay untouched.
5. **Scheduled sweep** — a `@Scheduled` monthly job resets monthly aggregates, applies boundary demotions, and performs catch-up re-evaluation.
6. **Subscription action idempotency** — a unique constraint enforces one `ACTIVE` subscription per user; subscribe/upgrade accept an `Idempotency-Key` (persisted and deduped) so a double-click or retry can't double-charge or double-subscribe. (Redis-backed keys are the multi-node swap; single-node uses the DB.)
7. **Status state machine** — subscription transitions are guarded: only `PENDING → ACTIVE`, `ACTIVE → {EXPIRED, CANCELLED}`, etc. are permitted; invalid transitions throw. The `charge → activate` payment flow uses `PENDING` and flips to `ACTIVE` only after the mock gateway confirms.
8. **Lock encloses the transaction** — every locked write runs as `withLock(userId → transaction)`, never a method-level `@Transactional` *around* the lock (the AOP proxy would commit *after* releasing the lock, leaking pre-commit state to the next thread). Same-node, same-user writes — order ingest, manual upgrade/downgrade, async tier evaluation — therefore serialize on one striped lock; `@Version` is the cross-node backstop, and a concurrent-modification conflict surfaces as `409` rather than `500`. A pessimistic `SELECT … FOR UPDATE` is the equivalent DB-level mechanism for a multi-node deployment.

---

## 6. Module / package structure

```
com.firstclub.membership
 ├─ catalog/      plan, tier, benefit, eligibility rules: entity · repo · service · controller
 ├─ subscription/ subscribe, upgrade, downgrade, cancel, status state machine, movement log (audit)
 ├─ order/        order-event ingestion + UserOrderStats
 ├─ tier/         TierEvaluationService, EligibilityRule strategies
 ├─ benefit/      BenefitHandler strategies + registry (effective benefits)
 ├─ payment/      PaymentGateway port + mock impl
 ├─ events/       DomainEventPublisher port + in-app adapter
 ├─ common/       error handling, concurrency utils (striped locks), idempotency
 └─ config/       seed data, async executor, scheduling
```

Each package is a bounded slice with its own entity/repo/service/controller where applicable. Cross-slice interaction goes through service interfaces and domain events, not shared mutable state.

---

## 7. Payment

Mocked behind a `PaymentGateway` port with an always-success default implementation. Subscribe/upgrade flows follow a realistic `charge → activate` sequence so the lifecycle is correct, while remaining swappable for a real gateway.

---

## 8. Error handling

A central `@RestControllerAdvice` maps domain exceptions to proper HTTP statuses with RFC-7807 problem-detail bodies:

| Exception | HTTP |
|-----------|------|
| `PlanNotFoundException` | 404 |
| `SubscriptionNotFoundException` | 404 |
| `AlreadySubscribedException` | 409 |
| `InvalidTierTransitionException` | 422 |
| `InvalidSubscriptionStateException` | 409 |
| duplicate idempotency key / in-flight request | 409 |
| `ObjectOptimisticLockingFailureException` (concurrent modification) | 409 |
| `PaymentFailedException` | 402 |
| validation errors | 400 |

---

## 9. Testing strategy

- **Unit** — eligibility-rule evaluation per strategy, `effectiveTier = MAX(purchased, earned)` resolution, benefit-handler resolution, state-machine transition guards, subscription lifecycle.
- **Idempotency** — same `orderId` ingested twice → counted once; double `POST /subscription` with the same `Idempotency-Key` → one subscription, one charge.
- **Concurrency** — N parallel order events + a concurrent manual upgrade for one user → assert correct final effective tier, no lost updates, no double-processing.
- **Audit** — each subscribe/upgrade/downgrade/auto-promotion appends exactly one `TierMovementLog` row with the right `kind` and from/to tiers.
- **Integration** — `MockMvc` / `@SpringBootTest` covering each API flow end-to-end, including a full progression demo (subscribe → ingest orders → auto-promote → read benefits → read history).

---

## 10. Stack & how to run

- **Java 21**, **Spring Boot 3.x**, **Spring Data JPA**, **H2** (in-memory, seeded on startup), **Maven wrapper**.
- Run: `./mvnw spring-boot:run` → APIs live immediately, no external setup.
- Postgres available via a Spring profile.
- A short **README** documents sample `curl`/HTTP calls demonstrating a full tier-progression flow.

---

## 11. Extensibility summary (maps to evaluation criteria)

| Change | Effort |
|--------|--------|
| New benefit type | Config rows + a `BenefitHandler` strategy bean (registry auto-wires it) |
| New tier | Config row + its `EligibilityRule` rows |
| New eligibility rule type | One new `EligibilityRule` strategy bean (auto-discovered); evaluator unchanged |
| Complex rule combos | AND today via config rows; OR-grouping is an additive extension |
| New plan cadence | Config row |
| Swap persistence (Postgres) | Spring profile |
| Swap payment provider | New `PaymentGateway` impl |
| Scale event bus (Kafka/RabbitMQ) | New `DomainEventPublisher` adapter — producers/consumers untouched |
| Scale the sweep | Partitioned Spring Batch job keyed by evaluation-period-end date (no full-table scan) |

---

## 12. Deliberately out of scope (with the scale-out seam)

Right-sized for a single-node, one-command demo; each item below is intentionally *not built*, but has a documented seam so the design scales without a rewrite. (Stating this explicitly is the stronger senior signal than pulling the infra into a take-home.)

- **Kafka / RabbitMQ** — in-app `ApplicationEventPublisher` today behind the `DomainEventPublisher` port; swap the adapter to publish `OrderCompleted` to a topic and have the membership consumer evaluate per user.
- **Redis idempotency cache** — DB-backed idempotency keys + a unique active-subscription constraint today; Redis is the multi-node cache for the `Idempotency-Key`.
- **Spring Batch** — the monthly `@Scheduled` sweep becomes a partitioned batch job querying only users whose evaluation period ends that day, rather than scanning all users.
- **Real checkout / payment gateway** — orders enter as events; payment is a mock behind `PaymentGateway`. The `BenefitHandler` interface is the seam where a real checkout would apply free-delivery / discounts.
