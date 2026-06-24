# FirstClub Membership Program

Tiered membership backend (Spring Boot, Java 21, H2 in-memory).

## Run

    ./mvnw spring-boot:run

APIs serve at http://localhost:8080. Seed data loads on startup
(plans, tiers SILVER/GOLD/PLATINUM, benefits, eligibility rules,
and two demo users id=1 Alice, id=2 Bob).

## Concepts

- **Plan** = billing cadence + price (MONTHLY/QUARTERLY/YEARLY).
- **Tier** = benefit level. `purchasedTier` (paid) and `earnedTier`
  (criteria) are decoupled; the applied tier is
  `effectiveTier = MAX(purchasedTier, earnedTier)`.
- **Auto-progression** is event-driven: each order event re-evaluates
  the user's tier (promote immediately; demote only at the monthly sweep).

## Demo flow

    # 1. Browse catalog
    curl localhost:8080/api/plans
    curl localhost:8080/api/tiers
    curl localhost:8080/api/benefits

    # 2. Subscribe user 1 to MONTHLY at SILVER (idempotent via header)
    curl -X POST localhost:8080/api/users/1/subscription \
      -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
      -d '{"planType":"MONTHLY","tier":"SILVER"}'

    # 3. Send 3 order events -> async auto-promotion to GOLD (>= 3 orders)
    for i in 1 2 3; do \
      curl -X POST localhost:8080/api/order-events \
        -H 'Content-Type: application/json' \
        -d "{\"orderId\":\"o-$i\",\"userId\":1,\"amount\":100}"; done

    # (optional) force evaluation synchronously for an instant demo
    curl -X POST localhost:8080/api/users/1/tier-evaluation

    # 4. Current membership + effective benefits
    curl localhost:8080/api/users/1/subscription
    curl localhost:8080/api/users/1/benefits

    # 5. Manual upgrade, then audit history
    curl -X PATCH localhost:8080/api/users/1/subscription/tier \
      -H 'Content-Type: application/json' -d '{"targetTier":"PLATINUM"}'
    curl localhost:8080/api/users/1/subscription/history

## Test

    ./mvnw test

Highlights: idempotent ingestion, MAX effective-tier resolution,
rule-engine progression, and a concurrency test (50 parallel order
events + a concurrent upgrade) proving no lost updates.

## Design notes

Full design + scale-out seams (Kafka/Redis/Spring Batch) are documented in
`docs/superpowers/specs/2026-06-24-firstclub-membership-design.md`.
