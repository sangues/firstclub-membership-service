# FirstClub Membership — Implementation Plan (Part 2: Tasks 4–12)

> Companion to `2026-06-24-firstclub-membership.md`. Same Global Constraints apply. Execute Tasks 1–3 first.

> **Concurrency invariant (applies to every locked write method below — subscribe, changeTier, cancel, ingest, evaluate):** the per-user `StripedLock` must **enclose** the database transaction, never the reverse. Method-level `@Transactional` would (via the AOP proxy) start the transaction *outside* the lock and commit *after* the lock releases, leaving a window where the next thread acquires the lock and reads pre-commit state — defeating serialization. The fix used throughout: **no method-level `@Transactional` on locked writes**; instead `locks.withLock(userId, () -> tx.execute(status -> { ... }))` with a programmatic `TransactionTemplate`. The lock stays in the service layer (not the controller) so direct service calls in tests are serialized too. Read-only methods keep `@Transactional(readOnly = true)` and need no lock.

---

## Task 4: User & order-stats foundation + StripedLock

**Files:**
- Create: `user/User.java`, `user/UserRepository.java`, `order/UserOrderStats.java`, `order/UserOrderStatsRepository.java`, `common/StripedLock.java`
- Modify: `config/DataSeeder.java` (seed two demo users)
- Test: `common/StripedLockTest.java`

**Interfaces:**
- Produces:
  - `User(Long id, String name, String cohort)`.
  - `UserRepository extends JpaRepository<User,Long>`.
  - `UserOrderStats(Long id, User user, long totalOrders, BigDecimal monthlyOrderValue, String statsMonth, Long version)`.
  - `UserOrderStatsRepository.findByUserId(Long): Optional<UserOrderStats>`.
  - `StripedLock.withLock(long key, Supplier<T>): T` and `withLock(long key, Runnable)`.

- [ ] **Step 1: Write the failing test** (`common/StripedLockTest.java`)

```java
package com.firstclub.membership.common;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class StripedLockTest {
    @Test
    void serializesMutationsForSameKey() throws Exception {
        StripedLock locks = new StripedLock(16);
        int[] shared = {0};
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger key = new AtomicInteger(7);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                locks.withLock(key.get(), () -> { shared[0] = shared[0] + 1; });
                done.countDown();
            });
        }
        done.await(5, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(shared[0]).isEqualTo(threads); // no lost updates
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=StripedLockTest`
Expected: FAIL — `StripedLock` does not exist.

- [ ] **Step 3: Write minimal implementation**

`common/StripedLock.java`:

```java
package com.firstclub.membership.common;

import org.springframework.stereotype.Component;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class StripedLock {
    private final ReentrantLock[] stripes;

    public StripedLock() { this(64); }

    public StripedLock(int size) {
        this.stripes = new ReentrantLock[size];
        for (int i = 0; i < size; i++) stripes[i] = new ReentrantLock();
    }

    public <T> T withLock(long key, Supplier<T> action) {
        ReentrantLock lock = stripes[(int) Math.floorMod(key, stripes.length)];
        lock.lock();
        try { return action.get(); }
        finally { lock.unlock(); }
    }

    public void withLock(long key, Runnable action) {
        withLock(key, () -> { action.run(); return null; });
    }
}
```

`user/User.java`:

```java
package com.firstclub.membership.user;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String cohort;
}
```

`user/UserRepository.java`:

```java
package com.firstclub.membership.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
```

`order/UserOrderStats.java`:

```java
package com.firstclub.membership.order;

import com.firstclub.membership.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "user_order_stats")
@Getter @Setter @NoArgsConstructor
public class UserOrderStats {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "user_id", unique = true)
    private User user;
    @Column(nullable = false)
    private long totalOrders = 0;
    @Column(nullable = false)
    private BigDecimal monthlyOrderValue = BigDecimal.ZERO;
    @Column
    private String statsMonth; // "YYYY-MM"
    @Version
    private Long version;

    public UserOrderStats(User user) { this.user = user; }
}
```

`order/UserOrderStatsRepository.java`:

```java
package com.firstclub.membership.order;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserOrderStatsRepository extends JpaRepository<UserOrderStats, Long> {
    Optional<UserOrderStats> findByUserId(Long userId);
}
```

Modify `config/DataSeeder.java` — inject `UserRepository users`; in `run`:

```java
if (users.count() == 0) {
    users.save(new User(null, "Alice", "STANDARD"));
    users.save(new User(null, "Bob", "VIP"));
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=StripedLockTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/common/StripedLock.java \
        src/main/java/com/firstclub/membership/user \
        src/main/java/com/firstclub/membership/order/UserOrderStats.java \
        src/main/java/com/firstclub/membership/order/UserOrderStatsRepository.java \
        src/main/java/com/firstclub/membership/config/DataSeeder.java \
        src/test/java/com/firstclub/membership/common/StripedLockTest.java
git commit -m "feat(user/order): user + order-stats entities, striped lock, seed users"
```

---

## Task 5: Subscription core — entity, state machine, payment, subscribe + get current

**Files:**
- Create: `subscription/SubscriptionStatus.java`, `subscription/Subscription.java`, `subscription/SubscriptionRepository.java`, `subscription/MovementKind.java`, `subscription/TierMovementLog.java`, `subscription/TierMovementLogRepository.java`, `subscription/payment/PaymentResult.java`, `subscription/payment/PaymentGateway.java`, `subscription/payment/MockPaymentGateway.java`, `subscription/SubscriptionService.java`, `subscription/SubscriptionController.java`, `subscription/dto/SubscribeRequest.java`, `subscription/dto/SubscriptionResponse.java`, `common/exception/*` (7 exceptions), `common/exception/GlobalExceptionHandler.java`, `config/PersistenceConfig.java`
- Test: `subscription/SubscriptionServiceTest.java`

**Interfaces:**
- Produces:
  - `SubscriptionStatus { PENDING, ACTIVE, EXPIRED, CANCELLED }` + `boolean canTransitionTo(SubscriptionStatus)`.
  - `Subscription(... purchasedTier, earnedTier(nullable), effectiveTier, status, startAt, expiresAt, idempotencyKey, version)` + `void recomputeEffectiveTier()`.
  - `SubscriptionRepository`: `findByUserIdAndStatus(Long, SubscriptionStatus)`, `findByIdempotencyKey(String)`, `findAllByStatus(SubscriptionStatus)`.
  - `PaymentGateway.charge(Long userId, BigDecimal amount, String idemKey): PaymentResult`.
  - `SubscriptionService.subscribe(Long userId, SubscribeRequest, String idemKey): SubscriptionResponse`; `getCurrent(Long userId): SubscriptionResponse`; helper `logMovement(User, String from, String to, MovementKind)`.
  - `SubscriptionResponse(Long userId, String planType, String purchasedTier, String earnedTier, String effectiveTier, String status, Instant startAt, Instant expiresAt)`.

- [ ] **Step 1: Write the failing test** (`subscription/SubscriptionServiceTest.java`)

```java
package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.subscription.dto.SubscriptionResponse;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SubscriptionServiceTest {
    @Autowired SubscriptionService service;
    @Autowired UserRepository users;

    @Test
    void subscribeActivatesWithChosenTier() {
        User u = users.save(new User(null, "Carol", "STANDARD"));
        SubscriptionResponse r = service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "GOLD"), null);
        assertThat(r.status()).isEqualTo("ACTIVE");
        assertThat(r.purchasedTier()).isEqualTo("GOLD");
        assertThat(r.effectiveTier()).isEqualTo("GOLD");
        assertThat(r.expiresAt()).isAfter(r.startAt());
    }

    @Test
    void subscribeDefaultsToLowestTierWhenNoneGiven() {
        User u = users.save(new User(null, "Dave", "STANDARD"));
        SubscriptionResponse r = service.subscribe(u.getId(), new SubscribeRequest("YEARLY", null), null);
        assertThat(r.purchasedTier()).isEqualTo("SILVER");
    }

    @Test
    void duplicateIdempotencyKeyReturnsSameSubscription() {
        User u = users.save(new User(null, "Erin", "STANDARD"));
        SubscriptionResponse first = service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), "key-1");
        SubscriptionResponse second = service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), "key-1");
        assertThat(second.status()).isEqualTo(first.status());
    }

    @Test
    void secondActiveSubscriptionRejected() {
        User u = users.save(new User(null, "Frank", "STANDARD"));
        service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        assertThatThrownBy(() -> service.subscribe(u.getId(), new SubscribeRequest("YEARLY", "GOLD"), null))
            .isInstanceOf(com.firstclub.membership.common.exception.AlreadySubscribedException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=SubscriptionServiceTest`
Expected: FAIL — subscription classes missing.

- [ ] **Step 3: Write minimal implementation**

`common/exception/` — seven `RuntimeException` subclasses (one file each):

```java
package com.firstclub.membership.common.exception;
public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(String m) { super(m); }
}
```
```java
package com.firstclub.membership.common.exception;
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String m) { super(m); }
}
```
```java
package com.firstclub.membership.common.exception;
public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(String m) { super(m); }
}
```
```java
package com.firstclub.membership.common.exception;
public class AlreadySubscribedException extends RuntimeException {
    public AlreadySubscribedException(String m) { super(m); }
}
```
```java
package com.firstclub.membership.common.exception;
public class InvalidSubscriptionStateException extends RuntimeException {
    public InvalidSubscriptionStateException(String m) { super(m); }
}
```
```java
package com.firstclub.membership.common.exception;
public class InvalidTierTransitionException extends RuntimeException {
    public InvalidTierTransitionException(String m) { super(m); }
}
```
```java
package com.firstclub.membership.common.exception;
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String m) { super(m); }
}
```

`common/exception/GlobalExceptionHandler.java`:

```java
package com.firstclub.membership.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({PlanNotFoundException.class, UserNotFoundException.class, SubscriptionNotFoundException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({AlreadySubscribedException.class, InvalidSubscriptionStateException.class})
    public ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // @Version backstop: the striped lock serializes same-user writes on one node, so
    // this is rare, but a multi-node deployment (or the read-only path) can still race.
    // Map it to a clean, retryable 409 instead of a raw 500.
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ProblemDetail concurrentModification(org.springframework.orm.ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Concurrent modification detected; please retry");
    }

    @ExceptionHandler(InvalidTierTransitionException.class)
    public ProblemDetail unprocessable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ProblemDetail paymentRequired(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                e.getBindingResult().getAllErrors().stream().findFirst()
                 .map(err -> err.getDefaultMessage()).orElse("Validation failed"));
    }
}
```

`config/PersistenceConfig.java` (provides the programmatic `TransactionTemplate` used by all locked writes):

```java
package com.firstclub.membership.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class PersistenceConfig {
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
```

`subscription/SubscriptionStatus.java`:

```java
package com.firstclub.membership.subscription;

import java.util.*;

public enum SubscriptionStatus {
    PENDING, ACTIVE, EXPIRED, CANCELLED;

    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> ALLOWED = Map.of(
        PENDING, EnumSet.of(ACTIVE, CANCELLED),
        ACTIVE, EnumSet.of(EXPIRED, CANCELLED),
        EXPIRED, EnumSet.noneOf(SubscriptionStatus.class),
        CANCELLED, EnumSet.noneOf(SubscriptionStatus.class)
    );

    public boolean canTransitionTo(SubscriptionStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
```

`subscription/Subscription.java`:

```java
package com.firstclub.membership.subscription;

import com.firstclub.membership.catalog.MembershipPlan;
import com.firstclub.membership.catalog.Tier;
import com.firstclub.membership.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "subscription")
@Getter @Setter @NoArgsConstructor
public class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(optional = false) @JoinColumn(name = "plan_id")
    private MembershipPlan plan;
    @ManyToOne(optional = false) @JoinColumn(name = "purchased_tier_id")
    private Tier purchasedTier;
    @ManyToOne @JoinColumn(name = "earned_tier_id")
    private Tier earnedTier;
    @ManyToOne(optional = false) @JoinColumn(name = "effective_tier_id")
    private Tier effectiveTier;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private SubscriptionStatus status;
    @Column(nullable = false)
    private Instant startAt;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(unique = true)
    private String idempotencyKey;
    @Version
    private Long version;

    public void recomputeEffectiveTier() {
        Tier eff = purchasedTier;
        if (earnedTier != null && earnedTier.getRank() > eff.getRank()) eff = earnedTier;
        this.effectiveTier = eff;
    }
}
```

`subscription/SubscriptionRepository.java`:

```java
package com.firstclub.membership.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);
    Optional<Subscription> findByIdempotencyKey(String idempotencyKey);
    List<Subscription> findAllByStatus(SubscriptionStatus status);
}
```

`subscription/MovementKind.java`:

```java
package com.firstclub.membership.subscription;

public enum MovementKind {
    SUBSCRIBE, UPGRADE, DOWNGRADE, EARNED_PROMOTION, EARNED_DEMOTION, RENEW, CANCEL
}
```

`subscription/TierMovementLog.java`:

```java
package com.firstclub.membership.subscription;

import com.firstclub.membership.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "tier_movement_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TierMovementLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(name = "from_tier")
    private String fromTier;
    @Column(name = "to_tier")
    private String toTier;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private MovementKind kind;
    @Column(nullable = false)
    private Instant at;
}
```

`subscription/TierMovementLogRepository.java`:

```java
package com.firstclub.membership.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TierMovementLogRepository extends JpaRepository<TierMovementLog, Long> {
    List<TierMovementLog> findByUserIdOrderByAtAsc(Long userId);
}
```

`subscription/payment/PaymentResult.java`:

```java
package com.firstclub.membership.subscription.payment;

public record PaymentResult(boolean success, String reference) {}
```

`subscription/payment/PaymentGateway.java`:

```java
package com.firstclub.membership.subscription.payment;

import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentResult charge(Long userId, BigDecimal amount, String idempotencyKey);
}
```

`subscription/payment/MockPaymentGateway.java`:

```java
package com.firstclub.membership.subscription.payment;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {
    @Override
    public PaymentResult charge(Long userId, BigDecimal amount, String idempotencyKey) {
        return new PaymentResult(true, "MOCK-" + UUID.randomUUID());
    }
}
```

`subscription/dto/SubscribeRequest.java`:

```java
package com.firstclub.membership.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscribeRequest(@NotBlank String planType, String tier) {}
```

`subscription/dto/SubscriptionResponse.java`:

```java
package com.firstclub.membership.subscription.dto;

import com.firstclub.membership.subscription.Subscription;
import java.time.Instant;

public record SubscriptionResponse(
        Long userId, String planType, String purchasedTier, String earnedTier,
        String effectiveTier, String status, Instant startAt, Instant expiresAt) {

    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.getUser().getId(),
                s.getPlan().getPlanType().name(),
                s.getPurchasedTier().getName(),
                s.getEarnedTier() == null ? null : s.getEarnedTier().getName(),
                s.getEffectiveTier().getName(),
                s.getStatus().name(),
                s.getStartAt(), s.getExpiresAt());
    }
}
```

`subscription/SubscriptionService.java`:

```java
package com.firstclub.membership.subscription;

import com.firstclub.membership.catalog.*;
import com.firstclub.membership.common.StripedLock;
import com.firstclub.membership.common.exception.*;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.subscription.dto.SubscriptionResponse;
import com.firstclub.membership.subscription.payment.PaymentGateway;
import com.firstclub.membership.subscription.payment.PaymentResult;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.Instant;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptions;
    private final TierMovementLogRepository movements;
    private final MembershipPlanRepository plans;
    private final TierRepository tiers;
    private final UserRepository users;
    private final PaymentGateway payment;
    private final StripedLock locks;
    private final TransactionTemplate tx;

    public SubscriptionService(SubscriptionRepository subscriptions, TierMovementLogRepository movements,
                               MembershipPlanRepository plans, TierRepository tiers, UserRepository users,
                               PaymentGateway payment, StripedLock locks, TransactionTemplate tx) {
        this.subscriptions = subscriptions; this.movements = movements; this.plans = plans;
        this.tiers = tiers; this.users = users; this.payment = payment; this.locks = locks; this.tx = tx;
    }

    // Lock ENCLOSES the transaction (withLock -> tx.execute); the idempotency check lives
    // INSIDE the lock to avoid the TOCTOU window. No method-level @Transactional here.
    public SubscriptionResponse subscribe(Long userId, SubscribeRequest req, String idempotencyKey) {
        return locks.withLock(userId, () -> tx.execute(status -> {
            if (idempotencyKey != null) {
                var existing = subscriptions.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) return SubscriptionResponse.from(existing.get());
            }
            User user = users.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("user " + userId));
            PlanType planType = parsePlanType(req.planType());
            MembershipPlan plan = plans.findByPlanType(planType)
                    .orElseThrow(() -> new PlanNotFoundException("plan " + req.planType()));
            subscriptions.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE).ifPresent(s -> {
                throw new AlreadySubscribedException("user " + userId + " already has an active subscription");
            });
            Tier purchased = (req.tier() == null || req.tier().isBlank())
                    ? tiers.findAllByOrderByRankAsc().get(0)
                    : tiers.findByName(req.tier())
                        .orElseThrow(() -> new InvalidTierTransitionException("unknown tier " + req.tier()));

            Subscription sub = new Subscription();
            sub.setUser(user);
            sub.setPlan(plan);
            sub.setPurchasedTier(purchased);
            sub.setEarnedTier(null);
            sub.setStatus(SubscriptionStatus.PENDING);
            Instant now = Instant.now();
            sub.setStartAt(now);
            sub.setExpiresAt(now.plus(Duration.ofDays(plan.getDurationDays())));
            sub.setIdempotencyKey(idempotencyKey);
            sub.recomputeEffectiveTier();

            PaymentResult result = payment.charge(userId, plan.getPrice(), idempotencyKey);
            if (!result.success()) throw new PaymentFailedException("payment declined for user " + userId);

            if (!sub.getStatus().canTransitionTo(SubscriptionStatus.ACTIVE))
                throw new InvalidSubscriptionStateException("cannot activate from " + sub.getStatus());
            sub.setStatus(SubscriptionStatus.ACTIVE);
            subscriptions.save(sub);
            logMovement(user, null, sub.getEffectiveTier().getName(), MovementKind.SUBSCRIBE);
            return SubscriptionResponse.from(sub);
        }));
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrent(Long userId) {
        Subscription sub = subscriptions.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException("no active subscription for user " + userId));
        return SubscriptionResponse.from(sub);
    }

    public void logMovement(User user, String from, String to, MovementKind kind) {
        movements.save(new TierMovementLog(null, user, from, to, kind, Instant.now()));
    }

    private PlanType parsePlanType(String value) {
        try { return PlanType.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new PlanNotFoundException("plan " + value); }
    }
}
```

`subscription/SubscriptionController.java`:

```java
package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.subscription.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/subscription")
public class SubscriptionController {
    private final SubscriptionService service;
    public SubscriptionController(SubscriptionService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@PathVariable Long userId,
                                          @Valid @RequestBody SubscribeRequest req,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.subscribe(userId, req, key);
    }

    @GetMapping
    public SubscriptionResponse current(@PathVariable Long userId) {
        return service.getCurrent(userId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=SubscriptionServiceTest`
Expected: PASS — all four cases.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/subscription \
        src/main/java/com/firstclub/membership/common/exception \
        src/main/java/com/firstclub/membership/config/PersistenceConfig.java \
        src/test/java/com/firstclub/membership/subscription/SubscriptionServiceTest.java
git commit -m "feat(subscription): subscribe + state machine + payment + idempotency + audit base"
```

---

## Task 6: Manual upgrade / downgrade / cancel + effective-tier MAX

**Files:**
- Modify: `subscription/SubscriptionService.java` (add `changeTier`, `cancel`), `subscription/SubscriptionController.java` (PATCH tier, DELETE)
- Create: `subscription/dto/ChangeTierRequest.java`
- Test: `subscription/EffectiveTierTest.java` (pure unit), `subscription/SubscriptionLifecycleTest.java`

**Interfaces:**
- Produces:
  - `SubscriptionService.changeTier(Long userId, String targetTier): SubscriptionResponse`.
  - `SubscriptionService.cancel(Long userId): SubscriptionResponse`.
  - `ChangeTierRequest(@NotBlank String targetTier)`.

- [ ] **Step 1: Write the failing tests**

`subscription/EffectiveTierTest.java`:

```java
package com.firstclub.membership.subscription;

import com.firstclub.membership.catalog.Tier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EffectiveTierTest {
    private Tier tier(String name, int rank) { return new Tier(null, name, rank, true); }

    @Test
    void effectiveIsHigherOfPurchasedAndEarned() {
        Subscription s = new Subscription();
        s.setPurchasedTier(tier("SILVER", 1));
        s.setEarnedTier(tier("GOLD", 2));
        s.recomputeEffectiveTier();
        assertThat(s.getEffectiveTier().getName()).isEqualTo("GOLD");
    }

    @Test
    void purchasedFloorWinsWhenHigherThanEarned() {
        Subscription s = new Subscription();
        s.setPurchasedTier(tier("PLATINUM", 3));
        s.setEarnedTier(tier("GOLD", 2));
        s.recomputeEffectiveTier();
        assertThat(s.getEffectiveTier().getName()).isEqualTo("PLATINUM");
    }
}
```

`subscription/SubscriptionLifecycleTest.java`:

```java
package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SubscriptionLifecycleTest {
    @Autowired SubscriptionService service;
    @Autowired UserRepository users;

    @Test
    void upgradeThenDowngradeMovesPurchasedTier() {
        User u = users.save(new User(null, "Grace", "STANDARD"));
        service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        assertThat(service.changeTier(u.getId(), "GOLD").effectiveTier()).isEqualTo("GOLD");
        assertThat(service.changeTier(u.getId(), "SILVER").effectiveTier()).isEqualTo("SILVER");
    }

    @Test
    void cancelThenCancelAgainRejected() {
        User u = users.save(new User(null, "Heidi", "STANDARD"));
        service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        service.cancel(u.getId());
        assertThatThrownBy(() -> service.cancel(u.getId()))
            .isInstanceOf(com.firstclub.membership.common.exception.SubscriptionNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q test -Dtest=EffectiveTierTest,SubscriptionLifecycleTest`
Expected: FAIL — `changeTier`/`cancel` not defined.

- [ ] **Step 3: Write minimal implementation**

Add to `subscription/SubscriptionService.java`:

```java
public SubscriptionResponse changeTier(Long userId, String targetTierName) {
    return locks.withLock(userId, () -> tx.execute(status -> {
        Subscription sub = subscriptions.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException("no active subscription for user " + userId));
        Tier target = tiers.findByName(targetTierName)
                .orElseThrow(() -> new InvalidTierTransitionException("unknown tier " + targetTierName));
        Tier previous = sub.getPurchasedTier();
        if (target.getRank() == previous.getRank())
            throw new InvalidTierTransitionException("already on tier " + targetTierName);

        MovementKind kind;
        if (target.getRank() > previous.getRank()) {
            PaymentResult result = payment.charge(userId, sub.getPlan().getPrice(), null);
            if (!result.success()) throw new PaymentFailedException("upgrade payment declined for user " + userId);
            kind = MovementKind.UPGRADE;
        } else {
            kind = MovementKind.DOWNGRADE;
        }
        sub.setPurchasedTier(target);
        sub.recomputeEffectiveTier();
        subscriptions.save(sub);
        logMovement(sub.getUser(), previous.getName(), target.getName(), kind);
        return SubscriptionResponse.from(sub);
    }));
}

public SubscriptionResponse cancel(Long userId) {
    return locks.withLock(userId, () -> tx.execute(status -> {
        Subscription sub = subscriptions.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException("no active subscription for user " + userId));
        if (!sub.getStatus().canTransitionTo(SubscriptionStatus.CANCELLED))
            throw new InvalidSubscriptionStateException("cannot cancel from " + sub.getStatus());
        sub.setStatus(SubscriptionStatus.CANCELLED);
        subscriptions.save(sub);
        logMovement(sub.getUser(), sub.getEffectiveTier().getName(), null, MovementKind.CANCEL);
        return SubscriptionResponse.from(sub);
    }));
}
```

`subscription/dto/ChangeTierRequest.java`:

```java
package com.firstclub.membership.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeTierRequest(@NotBlank String targetTier) {}
```

Add to `subscription/SubscriptionController.java`:

```java
@org.springframework.web.bind.annotation.PatchMapping("/tier")
public SubscriptionResponse changeTier(@PathVariable Long userId,
        @jakarta.validation.Valid @RequestBody com.firstclub.membership.subscription.dto.ChangeTierRequest req) {
    return service.changeTier(userId, req.targetTier());
}

@org.springframework.web.bind.annotation.DeleteMapping
public SubscriptionResponse cancel(@PathVariable Long userId) {
    return service.cancel(userId);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=EffectiveTierTest,SubscriptionLifecycleTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/subscription \
        src/test/java/com/firstclub/membership/subscription/EffectiveTierTest.java \
        src/test/java/com/firstclub/membership/subscription/SubscriptionLifecycleTest.java
git commit -m "feat(subscription): manual upgrade/downgrade/cancel with MAX effective tier"
```

---

## Task 7: Order ingestion (idempotent) + after-commit domain event

**Files:**
- Create: `events/DomainEventPublisher.java`, `events/SpringDomainEventPublisher.java`, `order/OrderIngestedEvent.java`, `order/OrderEvent.java`, `order/OrderEventRepository.java`, `order/OrderEventService.java`, `order/OrderEventController.java`, `order/dto/OrderEventRequest.java`
- Test: `order/OrderEventServiceTest.java`

**Interfaces:**
- Produces:
  - `DomainEventPublisher.publish(Object event)`.
  - `OrderIngestedEvent(Long userId)`.
  - `OrderEvent(Long id, String orderId(unique), User user, BigDecimal amount, Instant occurredAt, boolean processed)`.
  - `OrderEventRepository.existsByOrderId(String)`.
  - `OrderEventService.ingest(OrderEventRequest)`.
  - `OrderEventRequest(@NotBlank String orderId, @NotNull Long userId, @NotNull @Positive BigDecimal amount, Instant occurredAt)`.

- [ ] **Step 1: Write the failing test** (`order/OrderEventServiceTest.java`)

```java
package com.firstclub.membership.order;

import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderEventServiceTest {
    @Autowired OrderEventService service;
    @Autowired UserOrderStatsRepository stats;
    @Autowired UserRepository users;

    @Test
    void aggregatesOrdersAndIsIdempotent() {
        User u = users.save(new User(null, "Ivan", "STANDARD"));
        service.ingest(new OrderEventRequest("o-1", u.getId(), new BigDecimal("100"), Instant.now()));
        service.ingest(new OrderEventRequest("o-2", u.getId(), new BigDecimal("250"), Instant.now()));
        service.ingest(new OrderEventRequest("o-2", u.getId(), new BigDecimal("250"), Instant.now())); // duplicate

        UserOrderStats s = stats.findByUserId(u.getId()).orElseThrow();
        assertThat(s.getTotalOrders()).isEqualTo(2);
        assertThat(s.getMonthlyOrderValue()).isEqualByComparingTo("350");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=OrderEventServiceTest`
Expected: FAIL — order classes missing.

- [ ] **Step 3: Write minimal implementation**

`events/DomainEventPublisher.java`:

```java
package com.firstclub.membership.events;

public interface DomainEventPublisher {
    void publish(Object event);
}
```

`events/SpringDomainEventPublisher.java`:

```java
package com.firstclub.membership.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher delegate;
    public SpringDomainEventPublisher(ApplicationEventPublisher delegate) { this.delegate = delegate; }
    @Override public void publish(Object event) { delegate.publishEvent(event); }
}
```

`order/OrderIngestedEvent.java`:

```java
package com.firstclub.membership.order;

public record OrderIngestedEvent(Long userId) {}
```

`order/OrderEvent.java`:

```java
package com.firstclub.membership.order;

import com.firstclub.membership.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_event")
@Getter @Setter @NoArgsConstructor
public class OrderEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String orderId;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private Instant occurredAt;
    @Column(nullable = false)
    private boolean processed;
}
```

`order/OrderEventRepository.java`:

```java
package com.firstclub.membership.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    boolean existsByOrderId(String orderId);
}
```

`order/dto/OrderEventRequest.java`:

```java
package com.firstclub.membership.order.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderEventRequest(
        @NotBlank String orderId,
        @NotNull Long userId,
        @NotNull @Positive BigDecimal amount,
        Instant occurredAt) {}
```

`order/OrderEventService.java`:

```java
package com.firstclub.membership.order;

import com.firstclub.membership.common.StripedLock;
import com.firstclub.membership.common.exception.UserNotFoundException;
import com.firstclub.membership.events.DomainEventPublisher;
import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

@Service
public class OrderEventService {
    private final OrderEventRepository orders;
    private final UserOrderStatsRepository stats;
    private final UserRepository users;
    private final DomainEventPublisher events;
    private final StripedLock locks;
    private final TransactionTemplate tx;

    public OrderEventService(OrderEventRepository orders, UserOrderStatsRepository stats,
                             UserRepository users, DomainEventPublisher events, StripedLock locks,
                             TransactionTemplate tx) {
        this.orders = orders; this.stats = stats; this.users = users;
        this.events = events; this.locks = locks; this.tx = tx;
    }

    // Lock ENCLOSES the transaction so the order row + stats commit before the lock
    // releases; the after-commit event then fires for async tier evaluation.
    public void ingest(OrderEventRequest req) {
        locks.withLock(req.userId(), () -> tx.executeWithoutResult(status -> {
            if (orders.existsByOrderId(req.orderId())) return; // idempotent no-op
            User user = users.findById(req.userId())
                    .orElseThrow(() -> new UserNotFoundException("user " + req.userId()));
            Instant occurredAt = req.occurredAt() != null ? req.occurredAt() : Instant.now();

            OrderEvent event = new OrderEvent();
            event.setOrderId(req.orderId());
            event.setUser(user);
            event.setAmount(req.amount());
            event.setOccurredAt(occurredAt);
            event.setProcessed(true);
            orders.save(event);

            String month = YearMonth.from(occurredAt.atZone(ZoneOffset.UTC)).toString();
            UserOrderStats s = stats.findByUserId(user.getId()).orElseGet(() -> new UserOrderStats(user));
            if (!month.equals(s.getStatsMonth())) {
                s.setStatsMonth(month);
                s.setMonthlyOrderValue(java.math.BigDecimal.ZERO);
            }
            s.setTotalOrders(s.getTotalOrders() + 1);
            s.setMonthlyOrderValue(s.getMonthlyOrderValue().add(req.amount()));
            stats.save(s);

            events.publish(new OrderIngestedEvent(user.getId()));
        }));
    }
}
```

`order/OrderEventController.java`:

```java
package com.firstclub.membership.order;

import com.firstclub.membership.order.dto.OrderEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order-events")
public class OrderEventController {
    private final OrderEventService service;
    public OrderEventController(OrderEventService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void ingest(@Valid @RequestBody OrderEventRequest req) {
        service.ingest(req);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=OrderEventServiceTest`
Expected: PASS — 2 orders counted, duplicate ignored, monthly value 350.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/events \
        src/main/java/com/firstclub/membership/order \
        src/test/java/com/firstclub/membership/order/OrderEventServiceTest.java
git commit -m "feat(order): idempotent order ingestion + after-commit domain event"
```

---

## Task 8: Eligibility rule engine + async tier evaluation

**Files:**
- Create: `tier/RuleType.java`, `catalog/EligibilityRule.java`, `catalog/EligibilityRuleRepository.java`, `tier/EligibilityEvaluator.java`, `tier/OrderCountEvaluator.java`, `tier/OrderValueEvaluator.java`, `tier/CohortEvaluator.java`, `tier/TierEvaluationService.java`, `tier/TierEvaluationListener.java`, `tier/TierEvaluationController.java`, `config/AsyncConfig.java`
- Modify: `config/DataSeeder.java` (seed eligibility rules)
- Test: `tier/TierEvaluationServiceTest.java`

**Interfaces:**
- Produces:
  - `RuleType { ORDER_COUNT, ORDER_VALUE, COHORT }`.
  - `EligibilityRule(Long id, Tier tier, RuleType ruleType, Map<String,String> params)`.
  - `EligibilityRuleRepository.findByTierId(Long): List<EligibilityRule>`.
  - `EligibilityEvaluator { RuleType ruleType(); boolean matches(UserOrderStats stats, User user, Map<String,String> params); }`.
  - `TierEvaluationService.evaluate(Long userId, boolean allowDemotion)`.

- [ ] **Step 1: Write the failing test** (`tier/TierEvaluationServiceTest.java`)

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.order.OrderEventService;
import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.subscription.SubscriptionService;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TierEvaluationServiceTest {
    @Autowired SubscriptionService subs;
    @Autowired OrderEventService orders;
    @Autowired TierEvaluationService evaluator;
    @Autowired UserRepository users;

    @Test
    void promotesToGoldAfterThreeOrders() {
        User u = users.save(new User(null, "Judy", "STANDARD"));
        subs.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        for (int i = 0; i < 3; i++)
            orders.ingest(new OrderEventRequest("j-" + i, u.getId(), new BigDecimal("100"), Instant.now()));

        evaluator.evaluate(u.getId(), false);

        assertThat(subs.getCurrent(u.getId()).effectiveTier()).isEqualTo("GOLD");
        assertThat(subs.getCurrent(u.getId()).earnedTier()).isEqualTo("GOLD");
    }

    @Test
    void promotesToPlatinumWhenCountAndValueMet() {
        User u = users.save(new User(null, "Mallory", "STANDARD"));
        subs.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        for (int i = 0; i < 5; i++)
            orders.ingest(new OrderEventRequest("m-" + i, u.getId(), new BigDecimal("300"), Instant.now()));

        evaluator.evaluate(u.getId(), false);

        assertThat(subs.getCurrent(u.getId()).effectiveTier()).isEqualTo("PLATINUM");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=TierEvaluationServiceTest`
Expected: FAIL — evaluation classes missing.

- [ ] **Step 3: Write minimal implementation**

`tier/RuleType.java`:

```java
package com.firstclub.membership.tier;

public enum RuleType { ORDER_COUNT, ORDER_VALUE, COHORT }
```

`catalog/EligibilityRule.java`:

```java
package com.firstclub.membership.catalog;

import com.firstclub.membership.common.JsonMapConverter;
import com.firstclub.membership.tier.RuleType;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "eligibility_rule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EligibilityRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "tier_id")
    private Tier tier;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private RuleType ruleType;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "params", length = 2000)
    private Map<String, String> params = new HashMap<>();
}
```

`catalog/EligibilityRuleRepository.java`:

```java
package com.firstclub.membership.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EligibilityRuleRepository extends JpaRepository<EligibilityRule, Long> {
    List<EligibilityRule> findByTierId(Long tierId);
}
```

`tier/EligibilityEvaluator.java`:

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import java.util.Map;

public interface EligibilityEvaluator {
    RuleType ruleType();
    boolean matches(UserOrderStats stats, User user, Map<String, String> params);
}
```

`tier/OrderCountEvaluator.java`:

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class OrderCountEvaluator implements EligibilityEvaluator {
    @Override public RuleType ruleType() { return RuleType.ORDER_COUNT; }
    @Override public boolean matches(UserOrderStats stats, User user, Map<String, String> params) {
        long min = Long.parseLong(params.getOrDefault("minOrders", "0"));
        return stats.getTotalOrders() >= min;
    }
}
```

`tier/OrderValueEvaluator.java`:

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class OrderValueEvaluator implements EligibilityEvaluator {
    @Override public RuleType ruleType() { return RuleType.ORDER_VALUE; }
    @Override public boolean matches(UserOrderStats stats, User user, Map<String, String> params) {
        BigDecimal min = new BigDecimal(params.getOrDefault("minMonthlyValue", "0"));
        return stats.getMonthlyOrderValue().compareTo(min) >= 0;
    }
}
```

`tier/CohortEvaluator.java`:

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.user.User;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Map;

@Component
public class CohortEvaluator implements EligibilityEvaluator {
    @Override public RuleType ruleType() { return RuleType.COHORT; }
    @Override public boolean matches(UserOrderStats stats, User user, Map<String, String> params) {
        String allowed = params.getOrDefault("cohorts", "");
        return Arrays.stream(allowed.split(","))
                .map(String::trim)
                .anyMatch(c -> c.equalsIgnoreCase(user.getCohort()));
    }
}
```

`tier/TierEvaluationService.java`:

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.catalog.*;
import com.firstclub.membership.common.StripedLock;
import com.firstclub.membership.order.UserOrderStats;
import com.firstclub.membership.order.UserOrderStatsRepository;
import com.firstclub.membership.subscription.*;
import com.firstclub.membership.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class TierEvaluationService {
    private final SubscriptionRepository subscriptions;
    private final TierRepository tiers;
    private final EligibilityRuleRepository rules;
    private final UserOrderStatsRepository stats;
    private final SubscriptionService subscriptionService;
    private final StripedLock locks;
    private final TransactionTemplate tx;
    private final Map<RuleType, EligibilityEvaluator> evaluators = new EnumMap<>(RuleType.class);

    public TierEvaluationService(SubscriptionRepository subscriptions, TierRepository tiers,
                                 EligibilityRuleRepository rules, UserOrderStatsRepository stats,
                                 SubscriptionService subscriptionService, StripedLock locks,
                                 TransactionTemplate tx, List<EligibilityEvaluator> evaluatorBeans) {
        this.subscriptions = subscriptions; this.tiers = tiers; this.rules = rules;
        this.stats = stats; this.subscriptionService = subscriptionService; this.locks = locks; this.tx = tx;
        for (EligibilityEvaluator e : evaluatorBeans) evaluators.put(e.ruleType(), e);
    }

    // Lock ENCLOSES the transaction. Same StripedLock bean as subscribe/changeTier, so an
    // async evaluation and a manual tier change for the same user serialize (no lost update).
    public void evaluate(Long userId, boolean allowDemotion) {
        locks.withLock(userId, () -> tx.executeWithoutResult(status -> {
            Subscription sub = subscriptions.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE).orElse(null);
            if (sub == null) return;
            User user = sub.getUser();
            UserOrderStats userStats = stats.findByUserId(userId).orElseGet(() -> new UserOrderStats(user));

            Tier newEarned = highestQualifyingTier(user, userStats);
            int newRank = newEarned == null ? 0 : newEarned.getRank();
            int curRank = sub.getEarnedTier() == null ? 0 : sub.getEarnedTier().getRank();
            if (newRank == curRank) return;
            if (newRank < curRank && !allowDemotion) return; // promote-only mid-cycle

            int prevEffective = sub.getEffectiveTier().getRank();
            sub.setEarnedTier(newEarned);
            sub.recomputeEffectiveTier();
            subscriptions.save(sub);

            int nowEffective = sub.getEffectiveTier().getRank();
            if (nowEffective > prevEffective)
                subscriptionService.logMovement(user, rankName(prevEffective),
                        sub.getEffectiveTier().getName(), MovementKind.EARNED_PROMOTION);
            else if (nowEffective < prevEffective)
                subscriptionService.logMovement(user, rankName(prevEffective),
                        sub.getEffectiveTier().getName(), MovementKind.EARNED_DEMOTION);
        }));
    }

    private Tier highestQualifyingTier(User user, UserOrderStats userStats) {
        for (Tier tier : tiers.findAllByOrderByRankDesc()) {
            List<EligibilityRule> tierRules = rules.findByTierId(tier.getId());
            if (tierRules.isEmpty()) continue; // base tiers are not auto-earned
            boolean all = tierRules.stream().allMatch(r ->
                    evaluators.get(r.getRuleType()).matches(userStats, user, r.getParams()));
            if (all) return tier;
        }
        return null;
    }

    private String rankName(int rank) {
        return tiers.findAllByOrderByRankAsc().stream()
                .filter(t -> t.getRank() == rank).map(Tier::getName).findFirst().orElse(null);
    }
}
```

`tier/TierEvaluationListener.java`:

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.order.OrderIngestedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TierEvaluationListener {
    private final TierEvaluationService evaluation;
    public TierEvaluationListener(TierEvaluationService evaluation) { this.evaluation = evaluation; }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderIngested(OrderIngestedEvent event) {
        evaluation.evaluate(event.userId(), false);
    }
}
```

`config/AsyncConfig.java`:

```java
package com.firstclub.membership.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("tier-eval-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

`tier/TierEvaluationController.java`:

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.subscription.SubscriptionService;
import com.firstclub.membership.subscription.dto.SubscriptionResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/tier-evaluation")
public class TierEvaluationController {
    private final TierEvaluationService evaluation;
    private final SubscriptionService subscriptions;

    public TierEvaluationController(TierEvaluationService evaluation, SubscriptionService subscriptions) {
        this.evaluation = evaluation; this.subscriptions = subscriptions;
    }

    @PostMapping
    public SubscriptionResponse evaluate(@PathVariable Long userId) {
        evaluation.evaluate(userId, false);
        return subscriptions.getCurrent(userId);
    }
}
```

Modify `config/DataSeeder.java` — inject `EligibilityRuleRepository rules`; after benefits seeded:

```java
if (rules.count() == 0) {
    Tier gold = tiers.findByName("GOLD").orElseThrow();
    Tier platinum = tiers.findByName("PLATINUM").orElseThrow();
    rules.save(new EligibilityRule(null, gold, RuleType.ORDER_COUNT, new HashMap<>(Map.of("minOrders", "3"))));
    rules.save(new EligibilityRule(null, platinum, RuleType.ORDER_COUNT, new HashMap<>(Map.of("minOrders", "5"))));
    rules.save(new EligibilityRule(null, platinum, RuleType.ORDER_VALUE, new HashMap<>(Map.of("minMonthlyValue", "1000"))));
}
```

(Add imports `com.firstclub.membership.tier.RuleType` and `com.firstclub.membership.catalog.EligibilityRule` to the seeder.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=TierEvaluationServiceTest`
Expected: PASS — Gold after 3 orders, Platinum after 5×300 (count≥5 AND value≥1000).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/tier \
        src/main/java/com/firstclub/membership/catalog/EligibilityRule.java \
        src/main/java/com/firstclub/membership/catalog/EligibilityRuleRepository.java \
        src/main/java/com/firstclub/membership/config/AsyncConfig.java \
        src/main/java/com/firstclub/membership/config/DataSeeder.java \
        src/test/java/com/firstclub/membership/tier/TierEvaluationServiceTest.java
git commit -m "feat(tier): data-driven eligibility rule engine + async tier evaluation"
```

---

## Task 9: Effective benefits endpoint

**Files:**
- Create: `benefit/BenefitService.java`, `benefit/BenefitController.java`
- Test: extend `tier/TierEvaluationServiceTest.java` is not ideal — create `benefit/BenefitEndpointTest.java`

**Interfaces:**
- Produces: `BenefitService.effectiveBenefits(Long userId): List<ResolvedBenefit>`; `GET /api/users/{userId}/benefits`.
- Consumes: `CatalogService.benefitsForTier(Long tierId)` (Task 3), `SubscriptionRepository.findByUserIdAndStatus`.

- [ ] **Step 1: Write the failing test** (`benefit/BenefitEndpointTest.java`)

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.order.OrderEventService;
import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.subscription.SubscriptionService;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.tier.TierEvaluationService;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BenefitEndpointTest {
    @Autowired MockMvc mvc;
    @Autowired SubscriptionService subs;
    @Autowired OrderEventService orders;
    @Autowired TierEvaluationService evaluator;
    @Autowired UserRepository users;

    @Test
    void goldUserSeesTenPercentDiscount() throws Exception {
        User u = users.save(new User(null, "Niaj", "STANDARD"));
        subs.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        for (int i = 0; i < 3; i++)
            orders.ingest(new OrderEventRequest("n-" + i, u.getId(), new BigDecimal("100"), Instant.now()));
        evaluator.evaluate(u.getId(), false);

        mvc.perform(get("/api/users/{id}/benefits", u.getId()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.type=='PERCENT_DISCOUNT')].params.discountPercent").value(hasItem("10")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=BenefitEndpointTest`
Expected: FAIL — 404 (no benefits endpoint).

- [ ] **Step 3: Write minimal implementation**

`benefit/BenefitService.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.CatalogService;
import com.firstclub.membership.common.exception.SubscriptionNotFoundException;
import com.firstclub.membership.subscription.Subscription;
import com.firstclub.membership.subscription.SubscriptionRepository;
import com.firstclub.membership.subscription.SubscriptionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class BenefitService {
    private final SubscriptionRepository subscriptions;
    private final CatalogService catalog;

    public BenefitService(SubscriptionRepository subscriptions, CatalogService catalog) {
        this.subscriptions = subscriptions; this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<ResolvedBenefit> effectiveBenefits(Long userId) {
        Subscription sub = subscriptions.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException("no active subscription for user " + userId));
        return catalog.benefitsForTier(sub.getEffectiveTier().getId());
    }
}
```

`benefit/BenefitController.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/benefits")
public class BenefitController {
    private final BenefitService service;
    public BenefitController(BenefitService service) { this.service = service; }

    @GetMapping
    public List<ResolvedBenefit> benefits(@PathVariable Long userId) {
        return service.effectiveBenefits(userId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=BenefitEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/benefit/BenefitService.java \
        src/main/java/com/firstclub/membership/benefit/BenefitController.java \
        src/test/java/com/firstclub/membership/benefit/BenefitEndpointTest.java
git commit -m "feat(benefit): effective benefits endpoint for active subscription tier"
```

---

## Task 10: Subscription history (audit trail) endpoint

**Files:**
- Create: `subscription/dto/MovementResponse.java`
- Modify: `subscription/SubscriptionService.java` (add `history`), `subscription/SubscriptionController.java` (GET /history)
- Test: `subscription/SubscriptionHistoryTest.java`

**Interfaces:**
- Produces: `SubscriptionService.history(Long userId): List<MovementResponse>`; `GET /api/users/{userId}/subscription/history`; `MovementResponse(String fromTier, String toTier, String kind, Instant at)`.

- [ ] **Step 1: Write the failing test** (`subscription/SubscriptionHistoryTest.java`)

```java
package com.firstclub.membership.subscription;

import com.firstclub.membership.subscription.dto.MovementResponse;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SubscriptionHistoryTest {
    @Autowired SubscriptionService service;
    @Autowired UserRepository users;

    @Test
    void recordsSubscribeThenUpgrade() {
        User u = users.save(new User(null, "Olivia", "STANDARD"));
        service.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);
        service.changeTier(u.getId(), "GOLD");

        List<MovementResponse> history = service.history(u.getId());
        assertThat(history).extracting(MovementResponse::kind)
                .containsExactly("SUBSCRIBE", "UPGRADE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=SubscriptionHistoryTest`
Expected: FAIL — `history` / `MovementResponse` missing.

- [ ] **Step 3: Write minimal implementation**

`subscription/dto/MovementResponse.java`:

```java
package com.firstclub.membership.subscription.dto;

import com.firstclub.membership.subscription.TierMovementLog;
import java.time.Instant;

public record MovementResponse(String fromTier, String toTier, String kind, Instant at) {
    public static MovementResponse from(TierMovementLog log) {
        return new MovementResponse(log.getFromTier(), log.getToTier(), log.getKind().name(), log.getAt());
    }
}
```

Add to `subscription/SubscriptionService.java`:

```java
@Transactional(readOnly = true)
public java.util.List<com.firstclub.membership.subscription.dto.MovementResponse> history(Long userId) {
    return movements.findByUserIdOrderByAtAsc(userId).stream()
            .map(com.firstclub.membership.subscription.dto.MovementResponse::from).toList();
}
```

Add to `subscription/SubscriptionController.java`:

```java
@GetMapping("/history")
public java.util.List<com.firstclub.membership.subscription.dto.MovementResponse> history(@PathVariable Long userId) {
    return service.history(userId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=SubscriptionHistoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/subscription \
        src/test/java/com/firstclub/membership/subscription/SubscriptionHistoryTest.java
git commit -m "feat(subscription): tier movement history endpoint"
```

---

## Task 11: Scheduled monthly sweep + concurrency proof test

**Files:**
- Create: `config/SchedulingConfig.java`, `tier/TierSweepJob.java`
- Test: `tier/ConcurrencyTest.java`

**Interfaces:**
- Produces: `TierSweepJob.sweep()` (also invoked by `@Scheduled`).
- Consumes: `SubscriptionRepository.findAllByStatus(ACTIVE)`, `TierEvaluationService.evaluate(userId, true)`.

- [ ] **Step 1: Write the failing test** (`tier/ConcurrencyTest.java`)

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.order.OrderEventService;
import com.firstclub.membership.order.UserOrderStatsRepository;
import com.firstclub.membership.order.dto.OrderEventRequest;
import com.firstclub.membership.subscription.SubscriptionService;
import com.firstclub.membership.subscription.dto.SubscribeRequest;
import com.firstclub.membership.user.User;
import com.firstclub.membership.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyTest {
    @Autowired SubscriptionService subs;
    @Autowired OrderEventService orders;
    @Autowired TierEvaluationService evaluator;
    @Autowired UserOrderStatsRepository stats;
    @Autowired UserRepository users;

    @Test
    void parallelOrdersAndUpgradeStayConsistent() throws Exception {
        User u = users.save(new User(null, "Peggy", "STANDARD"));
        subs.subscribe(u.getId(), new SubscribeRequest("MONTHLY", "SILVER"), null);

        int n = 50;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch done = new CountDownLatch(n + 1);

        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    orders.ingest(new OrderEventRequest("p-" + idx, u.getId(), new BigDecimal("100"), Instant.now()));
                    if (idx % 5 == 0) // 10 duplicate re-submits
                        orders.ingest(new OrderEventRequest("p-" + idx, u.getId(), new BigDecimal("100"), Instant.now()));
                } finally { done.countDown(); }
            });
        }
        pool.submit(() -> { try { subs.changeTier(u.getId(), "GOLD"); } finally { done.countDown(); } });

        done.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        evaluator.evaluate(u.getId(), false);

        assertThat(stats.findByUserId(u.getId()).orElseThrow().getTotalOrders()).isEqualTo(n); // no lost updates / no dup
        // 50 orders -> PLATINUM earned; purchased GOLD -> effective = MAX = PLATINUM
        assertThat(subs.getCurrent(u.getId()).effectiveTier()).isEqualTo("PLATINUM");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=ConcurrencyTest`
Expected: FAIL — compiles and runs, but `TierSweepJob`/`SchedulingConfig` missing (and assertion if evaluation not wired). (If it passes purely on existing services, still add the sweep components below before committing.)

- [ ] **Step 3: Write minimal implementation**

`config/SchedulingConfig.java`:

```java
package com.firstclub.membership.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

`tier/TierSweepJob.java`:

```java
package com.firstclub.membership.tier;

import com.firstclub.membership.subscription.Subscription;
import com.firstclub.membership.subscription.SubscriptionRepository;
import com.firstclub.membership.subscription.SubscriptionStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TierSweepJob {
    private final SubscriptionRepository subscriptions;
    private final TierEvaluationService evaluation;

    public TierSweepJob(SubscriptionRepository subscriptions, TierEvaluationService evaluation) {
        this.subscriptions = subscriptions; this.evaluation = evaluation;
    }

    // 00:00 on the 1st of every month; demotions allowed at the boundary.
    @Scheduled(cron = "0 0 0 1 * *")
    public void sweep() {
        for (Subscription sub : subscriptions.findAllByStatus(SubscriptionStatus.ACTIVE)) {
            evaluation.evaluate(sub.getUser().getId(), true);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=ConcurrencyTest`
Expected: PASS — totalOrders == 50 (no lost updates, duplicates ignored), effective tier PLATINUM.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/config/SchedulingConfig.java \
        src/main/java/com/firstclub/membership/tier/TierSweepJob.java \
        src/test/java/com/firstclub/membership/tier/ConcurrencyTest.java
git commit -m "feat(tier): scheduled monthly sweep + concurrency proof test"
```

---

## Task 12: README + runnable demo flow

**Files:**
- Create: `README.md`

**Interfaces:** none (documentation + manual verification).

- [ ] **Step 1: Run the full test suite (acts as the failing/again-passing gate for docs)**

Run: `./mvnw -q test`
Expected: PASS — all suites green.

- [ ] **Step 2: Write `README.md`**

```markdown
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
```

- [ ] **Step 3: Manually verify the app runs**

Run: `./mvnw spring-boot:run` then execute the demo `curl` block in another shell.
Expected: subscription returns ACTIVE; after 3 order events (+ optional force eval) `effectiveTier` is `GOLD`; benefits include `PERCENT_DISCOUNT` 10; history shows `SUBSCRIBE` then `UPGRADE`.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: README with run instructions and demo flow"
```

---

## Self-Review (Part 2)

**Spec coverage:** subscribe/upgrade/downgrade/cancel (T5–T6), track membership+expiry (T5), order-driven criteria with order count/value/cohort evaluators (T8), MAX effective tier (T6/T8), idempotency for orders + subscriptions (T5/T7), concurrency via striped locks + @Version + async after-commit + scheduled sweep (T7/T8/T11), audit log + history (T5/T6/T8/T10), error handling (T5), benefits resolution (T9), runnable demo (T12). No gaps versus the design spec.

**Placeholder scan:** none — every step has complete code and exact run commands.

**Type consistency:** `evaluate(Long, boolean)`, `recomputeEffectiveTier()`, `effectiveTier()/earnedTier()/purchasedTier()`, `logMovement(User,String,String,MovementKind)`, `benefitsForTier(Long)`, `findByUserIdAndStatus`, `findByUserId`, `existsByOrderId`, `findByTierId`, `findAllByOrderByRankAsc/Desc`, `findAllByStatus` are used consistently between the tasks that define and consume them.
```