# FirstClub Membership Program Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a running, demo-able Spring Boot backend for a tiered membership program where users subscribe to billing plans, earn/purchase benefit tiers, and progress tiers automatically from order activity — safely under concurrency.

**Architecture:** A single Spring Boot service over H2 (in-memory) with Spring Data JPA. Domain is split into bounded packages (catalog, subscription, order, tier, benefit, events). Tier is decoupled into `purchasedTier` (billing) + `earnedTier` (criteria); the applied tier is `effectiveTier = MAX(...)`. Order events are ingested idempotently, update per-user stats under a striped lock, and publish an after-commit domain event that triggers async tier re-evaluation. Eligibility and benefits are data-driven strategy beans.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Web, Spring Data JPA, Bean Validation, H2, Lombok, JUnit 5, Spring Boot Test (MockMvc), Maven.

## Global Constraints

- Java 21; Spring Boot 3.3.x; build with the Maven wrapper (`./mvnw`).
- Persistence: H2 in-memory only; one-command run `./mvnw spring-boot:run`; no external infra (no Kafka/Redis/Docker).
- Base package: `com.firstclub.membership`.
- All amounts/prices: `java.math.BigDecimal`. All timestamps: `java.time.Instant` (UTC).
- DTOs are Java `record`s, distinct from JPA entities; mapping lives in services.
- Tiers are data rows keyed by `name` + `rank` (NOT a Java enum) so new tiers need no code change.
- Effective tier = highest `rank` among `{purchasedTier, earnedTier}` (earned may be null).
- Auto-progression promotes immediately; demotion only via the scheduled sweep.
- Every tier/status change appends one immutable `TierMovementLog` row.
- TDD: write the failing test first, watch it fail, implement minimally, watch it pass, commit.

---

## File Structure

```
pom.xml
src/main/resources/application.yml
src/main/java/com/firstclub/membership/
  MembershipApplication.java
  common/
    JsonMapConverter.java          # Map<String,String> <-> JSON column
    StripedLock.java               # per-user lock striping
    exception/                     # domain exceptions + GlobalExceptionHandler
  catalog/
    PlanType.java  MembershipPlan.java  MembershipPlanRepository.java
    Tier.java  TierRepository.java
    BenefitType.java  Benefit.java  BenefitRepository.java
    TierBenefit.java  TierBenefitRepository.java
    EligibilityRule.java  RuleType.java  EligibilityRuleRepository.java
    CatalogService.java  CatalogController.java
    dto/ PlanResponse  TierResponse  BenefitResponse
  user/
    User.java  UserRepository.java
  order/
    OrderEvent.java  OrderEventRepository.java
    UserOrderStats.java  UserOrderStatsRepository.java
    OrderEventService.java  OrderEventController.java
    OrderIngestedEvent.java  dto/OrderEventRequest
  events/
    DomainEventPublisher.java  SpringDomainEventPublisher.java
  benefit/
    BenefitHandler.java  *Handler.java (5)  BenefitHandlerRegistry.java
    BenefitService.java  BenefitController.java  dto/ResolvedBenefit
  tier/
    EligibilityEvaluator.java  OrderCountEvaluator.java  OrderValueEvaluator.java
    CohortEvaluator.java  TierEvaluationService.java  TierEvaluationListener.java
    TierEvaluationController.java  TierSweepJob.java
  subscription/
    SubscriptionStatus.java  Subscription.java  SubscriptionRepository.java
    MovementKind.java  TierMovementLog.java  TierMovementLogRepository.java
    payment/ PaymentGateway  MockPaymentGateway  PaymentResult
    SubscriptionService.java  SubscriptionController.java
    dto/ SubscribeRequest  ChangeTierRequest  SubscriptionResponse  MovementResponse
  config/
    DataSeeder.java  AsyncConfig.java  SchedulingConfig.java
src/test/java/com/firstclub/membership/...   # mirrors main
```

---

## Task 1: Project scaffold & boot smoke test

**Files:**
- Create: `pom.xml`, `src/main/resources/application.yml`, `src/main/java/com/firstclub/membership/MembershipApplication.java`
- Test: `src/test/java/com/firstclub/membership/MembershipApplicationTests.java`

**Interfaces:**
- Produces: a bootable Spring context; base package `com.firstclub.membership`.

- [ ] **Step 1: Write the failing test**

```java
package com.firstclub.membership;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MembershipApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=MembershipApplicationTests`
Expected: FAIL — no `pom.xml` / Maven wrapper yet (`mvnw: No such file or directory` or build error).

- [ ] **Step 3: Create the project files**

`pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>
    <groupId>com.firstclub</groupId>
    <artifactId>membership</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>21</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:membership;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: create-drop
    open-in-view: false
    properties:
      hibernate.format_sql: true
  h2:
    console:
      enabled: true
logging:
  level:
    com.firstclub.membership: INFO
```

`MembershipApplication.java`:

```java
package com.firstclub.membership;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MembershipApplication {
    public static void main(String[] args) {
        SpringApplication.run(MembershipApplication.class, args);
    }
}
```

Then generate the Maven wrapper: `mvn -q wrapper:wrapper -Dmaven=3.9.9` (or copy an existing `.mvn/wrapper` + `mvnw`/`mvnw.cmd` if Maven is not installed).

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=MembershipApplicationTests`
Expected: PASS — Spring context starts and stops cleanly.

- [ ] **Step 5: Commit**

```bash
git add pom.xml mvnw mvnw.cmd .mvn src/main/resources/application.yml \
        src/main/java/com/firstclub/membership/MembershipApplication.java \
        src/test/java/com/firstclub/membership/MembershipApplicationTests.java
git commit -m "chore: scaffold Spring Boot project with H2 + boot smoke test"
```

---

## Task 2: Catalog — Plans & Tiers (entities, repos, seed, read APIs)

**Files:**
- Create: `catalog/PlanType.java`, `catalog/MembershipPlan.java`, `catalog/MembershipPlanRepository.java`, `catalog/Tier.java`, `catalog/TierRepository.java`, `catalog/CatalogService.java`, `catalog/CatalogController.java`, `catalog/dto/PlanResponse.java`, `catalog/dto/TierResponse.java`, `config/DataSeeder.java`
- Test: `catalog/CatalogControllerTest.java`

**Interfaces:**
- Produces:
  - `PlanType { MONTHLY(30), QUARTERLY(90), YEARLY(365) }` with `int durationDays()`.
  - `MembershipPlan(Long id, PlanType planType, BigDecimal price, int durationDays, boolean active)`.
  - `Tier(Long id, String name, int rank, boolean active)`.
  - `MembershipPlanRepository extends JpaRepository<MembershipPlan,Long>` + `Optional<MembershipPlan> findByPlanType(PlanType)`.
  - `TierRepository extends JpaRepository<Tier,Long>` + `Optional<Tier> findByName(String)`, `List<Tier> findAllByOrderByRankAsc()`.
  - `CatalogService.listPlans(): List<PlanResponse>`, `listTiers(): List<TierResponse>`.
  - `TierResponse(Long id, String name, int rank, List<ResolvedBenefit> benefits)` — `benefits` is empty until Task 3.

- [ ] **Step 1: Write the failing test**

```java
package com.firstclub.membership.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void listsSeededPlans() throws Exception {
        mvc.perform(get("/api/plans"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.planType=='MONTHLY')]").exists())
           .andExpect(jsonPath("$[?(@.planType=='YEARLY')]").exists());
    }

    @Test
    void listsSeededTiersByRank() throws Exception {
        mvc.perform(get("/api/tiers"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("SILVER"))
           .andExpect(jsonPath("$[2].name").value("PLATINUM"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=CatalogControllerTest`
Expected: FAIL — 404 (no controller) / classes missing.

- [ ] **Step 3: Write minimal implementation**

`catalog/PlanType.java`:

```java
package com.firstclub.membership.catalog;

public enum PlanType {
    MONTHLY(30), QUARTERLY(90), YEARLY(365);
    private final int durationDays;
    PlanType(int durationDays) { this.durationDays = durationDays; }
    public int durationDays() { return durationDays; }
}
```

`catalog/MembershipPlan.java`:

```java
package com.firstclub.membership.catalog;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "membership_plan")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MembershipPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PlanType planType;
    @Column(nullable = false)
    private BigDecimal price;
    @Column(nullable = false)
    private int durationDays;
    @Column(nullable = false)
    private boolean active = true;
}
```

`catalog/Tier.java`:

```java
package com.firstclub.membership.catalog;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tier")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Tier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(nullable = false)
    private int rank;
    @Column(nullable = false)
    private boolean active = true;
}
```

`catalog/MembershipPlanRepository.java`:

```java
package com.firstclub.membership.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {
    Optional<MembershipPlan> findByPlanType(PlanType planType);
}
```

`catalog/TierRepository.java`:

```java
package com.firstclub.membership.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TierRepository extends JpaRepository<Tier, Long> {
    Optional<Tier> findByName(String name);
    List<Tier> findAllByOrderByRankAsc();
    List<Tier> findAllByOrderByRankDesc();
}
```

`catalog/dto/PlanResponse.java`:

```java
package com.firstclub.membership.catalog.dto;

import com.firstclub.membership.catalog.MembershipPlan;
import java.math.BigDecimal;

public record PlanResponse(Long id, String planType, BigDecimal price, int durationDays) {
    public static PlanResponse from(MembershipPlan p) {
        return new PlanResponse(p.getId(), p.getPlanType().name(), p.getPrice(), p.getDurationDays());
    }
}
```

`catalog/dto/TierResponse.java`:

```java
package com.firstclub.membership.catalog.dto;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import java.util.List;

public record TierResponse(Long id, String name, int rank, List<ResolvedBenefit> benefits) {
}
```

> Create a placeholder `benefit/dto/ResolvedBenefit.java` now so this compiles; Task 3 fills its handlers:
```java
package com.firstclub.membership.benefit.dto;
import java.util.Map;
public record ResolvedBenefit(String type, String description, Map<String, String> params) {}
```

`catalog/CatalogService.java`:

```java
package com.firstclub.membership.catalog;

import com.firstclub.membership.catalog.dto.PlanResponse;
import com.firstclub.membership.catalog.dto.TierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CatalogService {
    private final MembershipPlanRepository plans;
    private final TierRepository tiers;

    public CatalogService(MembershipPlanRepository plans, TierRepository tiers) {
        this.plans = plans;
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return plans.findAll().stream().map(PlanResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TierResponse> listTiers() {
        return tiers.findAllByOrderByRankAsc().stream()
                .map(t -> new TierResponse(t.getId(), t.getName(), t.getRank(), List.of()))
                .toList();
    }
}
```

`catalog/CatalogController.java`:

```java
package com.firstclub.membership.catalog;

import com.firstclub.membership.catalog.dto.PlanResponse;
import com.firstclub.membership.catalog.dto.TierResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/plans")
    public List<PlanResponse> plans() { return catalog.listPlans(); }

    @GetMapping("/tiers")
    public List<TierResponse> tiers() { return catalog.listTiers(); }
}
```

`config/DataSeeder.java`:

```java
package com.firstclub.membership.config;

import com.firstclub.membership.catalog.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class DataSeeder implements ApplicationRunner {
    private final MembershipPlanRepository plans;
    private final TierRepository tiers;

    public DataSeeder(MembershipPlanRepository plans, TierRepository tiers) {
        this.plans = plans;
        this.tiers = tiers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (plans.count() == 0) {
            plans.save(new MembershipPlan(null, PlanType.MONTHLY, new BigDecimal("199"), PlanType.MONTHLY.durationDays(), true));
            plans.save(new MembershipPlan(null, PlanType.QUARTERLY, new BigDecimal("499"), PlanType.QUARTERLY.durationDays(), true));
            plans.save(new MembershipPlan(null, PlanType.YEARLY, new BigDecimal("1499"), PlanType.YEARLY.durationDays(), true));
        }
        if (tiers.count() == 0) {
            tiers.save(new Tier(null, "SILVER", 1, true));
            tiers.save(new Tier(null, "GOLD", 2, true));
            tiers.save(new Tier(null, "PLATINUM", 3, true));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=CatalogControllerTest`
Expected: PASS — both endpoints return seeded data, tiers ordered by rank.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/catalog \
        src/main/java/com/firstclub/membership/benefit/dto/ResolvedBenefit.java \
        src/main/java/com/firstclub/membership/config/DataSeeder.java \
        src/test/java/com/firstclub/membership/catalog/CatalogControllerTest.java
git commit -m "feat(catalog): plans & tiers entities, seed data, read APIs"
```

---

## Task 3: Benefits — catalog, JSON params, handler registry, GET /api/benefits

**Files:**
- Create: `common/JsonMapConverter.java`, `catalog/BenefitType.java`, `catalog/Benefit.java`, `catalog/BenefitRepository.java`, `catalog/TierBenefit.java`, `catalog/TierBenefitRepository.java`, `benefit/BenefitHandler.java`, `benefit/PercentDiscountHandler.java`, `benefit/FreeDeliveryHandler.java`, `benefit/ExclusiveDealsHandler.java`, `benefit/EarlyAccessHandler.java`, `benefit/PrioritySupportHandler.java`, `benefit/BenefitHandlerRegistry.java`, `catalog/dto/BenefitResponse.java`
- Modify: `catalog/CatalogService.java` (add `listBenefits`, populate `TierResponse.benefits`), `catalog/CatalogController.java` (add `GET /api/benefits`), `config/DataSeeder.java` (seed benefits + tier-benefit mappings)
- Test: `benefit/BenefitHandlerRegistryTest.java`, extend `catalog/CatalogControllerTest.java`

**Interfaces:**
- Produces:
  - `BenefitType { FREE_DELIVERY, PERCENT_DISCOUNT, EXCLUSIVE_DEALS, EARLY_ACCESS, PRIORITY_SUPPORT }`.
  - `Benefit(Long id, BenefitType type, String description, Map<String,String> params)`.
  - `TierBenefit(Long id, Tier tier, Benefit benefit, Map<String,String> paramOverrides)`.
  - `TierBenefitRepository.findByTierId(Long): List<TierBenefit>`.
  - `BenefitHandler { BenefitType type(); ResolvedBenefit resolve(Benefit b, Map<String,String> effectiveParams); }`.
  - `BenefitHandlerRegistry.resolve(Benefit b, Map<String,String> overrides): ResolvedBenefit`.

- [ ] **Step 1: Write the failing test**

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BenefitHandlerRegistryTest {
    @Autowired BenefitHandlerRegistry registry;

    @Test
    void overrideParamsWinOverBenefitDefaults() {
        Benefit b = new Benefit(1L, BenefitType.PERCENT_DISCOUNT, "Discount", Map.of("discountPercent", "5"));
        ResolvedBenefit r = registry.resolve(b, Map.of("discountPercent", "15"));
        assertThat(r.type()).isEqualTo("PERCENT_DISCOUNT");
        assertThat(r.params().get("discountPercent")).isEqualTo("15");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=BenefitHandlerRegistryTest`
Expected: FAIL — registry/handlers missing.

- [ ] **Step 3: Write minimal implementation**

`common/JsonMapConverter.java`:

```java
package com.firstclub.membership.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, String>, String> {
    private static final ObjectMapper M = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        try { return attribute == null ? "{}" : M.writeValueAsString(attribute); }
        catch (Exception e) { throw new IllegalStateException("serialize params", e); }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        try { return (dbData == null || dbData.isBlank()) ? new HashMap<>() : M.readValue(dbData, TYPE); }
        catch (Exception e) { throw new IllegalStateException("deserialize params", e); }
    }
}
```

`catalog/BenefitType.java`:

```java
package com.firstclub.membership.catalog;

public enum BenefitType {
    FREE_DELIVERY, PERCENT_DISCOUNT, EXCLUSIVE_DEALS, EARLY_ACCESS, PRIORITY_SUPPORT
}
```

`catalog/Benefit.java`:

```java
package com.firstclub.membership.catalog;

import com.firstclub.membership.common.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "benefit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Benefit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BenefitType type;
    @Column(nullable = false)
    private String description;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "params", length = 2000)
    private Map<String, String> params = new HashMap<>();
}
```

`catalog/Benefit Repository` → `catalog/BenefitRepository.java`:

```java
package com.firstclub.membership.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitRepository extends JpaRepository<Benefit, Long> {
}
```

`catalog/TierBenefit.java`:

```java
package com.firstclub.membership.catalog;

import com.firstclub.membership.common.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "tier_benefit", uniqueConstraints = @UniqueConstraint(columnNames = {"tier_id", "benefit_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TierBenefit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "tier_id")
    private Tier tier;
    @ManyToOne(optional = false) @JoinColumn(name = "benefit_id")
    private Benefit benefit;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "param_overrides", length = 2000)
    private Map<String, String> paramOverrides = new HashMap<>();
}
```

`catalog/TierBenefitRepository.java`:

```java
package com.firstclub.membership.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TierBenefitRepository extends JpaRepository<TierBenefit, Long> {
    List<TierBenefit> findByTierId(Long tierId);
}
```

`benefit/BenefitHandler.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import java.util.Map;

public interface BenefitHandler {
    BenefitType type();
    ResolvedBenefit resolve(Benefit benefit, Map<String, String> effectiveParams);
}
```

`benefit/PercentDiscountHandler.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PercentDiscountHandler implements BenefitHandler {
    @Override public BenefitType type() { return BenefitType.PERCENT_DISCOUNT; }
    @Override public ResolvedBenefit resolve(Benefit b, Map<String, String> params) {
        String pct = params.getOrDefault("discountPercent", "0");
        return new ResolvedBenefit(b.getType().name(),
                "Extra " + pct + "% discount on selected items", params);
    }
}
```

`benefit/FreeDeliveryHandler.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class FreeDeliveryHandler implements BenefitHandler {
    @Override public BenefitType type() { return BenefitType.FREE_DELIVERY; }
    @Override public ResolvedBenefit resolve(Benefit b, Map<String, String> params) {
        return new ResolvedBenefit(b.getType().name(), "Free delivery on eligible orders", params);
    }
}
```

`benefit/ExclusiveDealsHandler.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ExclusiveDealsHandler implements BenefitHandler {
    @Override public BenefitType type() { return BenefitType.EXCLUSIVE_DEALS; }
    @Override public ResolvedBenefit resolve(Benefit b, Map<String, String> params) {
        return new ResolvedBenefit(b.getType().name(), "Access to exclusive deals", params);
    }
}
```

`benefit/EarlyAccessHandler.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class EarlyAccessHandler implements BenefitHandler {
    @Override public BenefitType type() { return BenefitType.EARLY_ACCESS; }
    @Override public ResolvedBenefit resolve(Benefit b, Map<String, String> params) {
        return new ResolvedBenefit(b.getType().name(), "Early access to sales", params);
    }
}
```

`benefit/PrioritySupportHandler.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PrioritySupportHandler implements BenefitHandler {
    @Override public BenefitType type() { return BenefitType.PRIORITY_SUPPORT; }
    @Override public ResolvedBenefit resolve(Benefit b, Map<String, String> params) {
        return new ResolvedBenefit(b.getType().name(), "Priority customer support", params);
    }
}
```

`benefit/BenefitHandlerRegistry.java`:

```java
package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class BenefitHandlerRegistry {
    private final Map<BenefitType, BenefitHandler> handlers = new EnumMap<>(BenefitType.class);

    public BenefitHandlerRegistry(List<BenefitHandler> all) {
        for (BenefitHandler h : all) handlers.put(h.type(), h);
    }

    public ResolvedBenefit resolve(Benefit benefit, Map<String, String> overrides) {
        Map<String, String> merged = new HashMap<>(benefit.getParams());
        if (overrides != null) merged.putAll(overrides);
        BenefitHandler handler = handlers.get(benefit.getType());
        if (handler == null) throw new IllegalStateException("No handler for " + benefit.getType());
        return handler.resolve(benefit, merged);
    }
}
```

`catalog/dto/BenefitResponse.java`:

```java
package com.firstclub.membership.catalog.dto;

import com.firstclub.membership.catalog.Benefit;
import java.util.Map;

public record BenefitResponse(Long id, String type, String description, Map<String, String> params) {
    public static BenefitResponse from(Benefit b) {
        return new BenefitResponse(b.getId(), b.getType().name(), b.getDescription(), b.getParams());
    }
}
```

Modify `catalog/CatalogService.java` — inject `BenefitRepository`, `TierBenefitRepository`, `BenefitHandlerRegistry`; add `listBenefits` and populate tier benefits:

```java
// add fields + constructor params:
private final BenefitRepository benefits;
private final TierBenefitRepository tierBenefits;
private final com.firstclub.membership.benefit.BenefitHandlerRegistry benefitRegistry;

@Transactional(readOnly = true)
public java.util.List<com.firstclub.membership.catalog.dto.BenefitResponse> listBenefits() {
    return benefits.findAll().stream()
            .map(com.firstclub.membership.catalog.dto.BenefitResponse::from).toList();
}

@Transactional(readOnly = true)
public java.util.List<com.firstclub.membership.benefit.dto.ResolvedBenefit> benefitsForTier(Long tierId) {
    return tierBenefits.findByTierId(tierId).stream()
            .map(tb -> benefitRegistry.resolve(tb.getBenefit(), tb.getParamOverrides()))
            .toList();
}
```

And change `listTiers()` to use `benefitsForTier(t.getId())` instead of `List.of()`.

Modify `catalog/CatalogController.java` — add:

```java
@GetMapping("/benefits")
public java.util.List<com.firstclub.membership.catalog.dto.BenefitResponse> benefits() {
    return catalog.listBenefits();
}
```

Modify `config/DataSeeder.java` — inject `BenefitRepository`, `TierBenefitRepository`; after tiers are seeded:

```java
if (benefits.count() == 0) {
    Benefit freeDelivery = benefits.save(new Benefit(null, BenefitType.FREE_DELIVERY, "Free delivery", new HashMap<>()));
    Benefit discount = benefits.save(new Benefit(null, BenefitType.PERCENT_DISCOUNT, "Discount", new HashMap<>(Map.of("discountPercent", "5"))));
    Benefit deals = benefits.save(new Benefit(null, BenefitType.EXCLUSIVE_DEALS, "Exclusive deals", new HashMap<>()));
    Benefit early = benefits.save(new Benefit(null, BenefitType.EARLY_ACCESS, "Early access", new HashMap<>()));
    Benefit priority = benefits.save(new Benefit(null, BenefitType.PRIORITY_SUPPORT, "Priority support", new HashMap<>()));

    Tier silver = tiers.findByName("SILVER").orElseThrow();
    Tier gold = tiers.findByName("GOLD").orElseThrow();
    Tier platinum = tiers.findByName("PLATINUM").orElseThrow();

    tierBenefits.save(new TierBenefit(null, silver, freeDelivery, new HashMap<>()));
    tierBenefits.save(new TierBenefit(null, gold, freeDelivery, new HashMap<>()));
    tierBenefits.save(new TierBenefit(null, gold, discount, new HashMap<>(Map.of("discountPercent", "10"))));
    tierBenefits.save(new TierBenefit(null, gold, deals, new HashMap<>()));
    tierBenefits.save(new TierBenefit(null, platinum, freeDelivery, new HashMap<>()));
    tierBenefits.save(new TierBenefit(null, platinum, discount, new HashMap<>(Map.of("discountPercent", "15"))));
    tierBenefits.save(new TierBenefit(null, platinum, deals, new HashMap<>()));
    tierBenefits.save(new TierBenefit(null, platinum, early, new HashMap<>()));
    tierBenefits.save(new TierBenefit(null, platinum, priority, new HashMap<>()));
}
```

Add to `CatalogControllerTest`:

```java
@Test
void platinumTierExposesDiscountFifteen() throws Exception {
    mvc.perform(get("/api/tiers"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$[2].name").value("PLATINUM"))
       .andExpect(jsonPath("$[2].benefits[?(@.type=='PERCENT_DISCOUNT')].params.discountPercent").value(org.hamcrest.Matchers.hasItem("15")));
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q test -Dtest=BenefitHandlerRegistryTest,CatalogControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/firstclub/membership/common/JsonMapConverter.java \
        src/main/java/com/firstclub/membership/catalog \
        src/main/java/com/firstclub/membership/benefit \
        src/main/java/com/firstclub/membership/config/DataSeeder.java \
        src/test/java/com/firstclub/membership/benefit \
        src/test/java/com/firstclub/membership/catalog/CatalogControllerTest.java
git commit -m "feat(benefit): data-driven benefits, handler registry, GET /api/benefits"
```

---

> **Remaining tasks (4–12) continue in the same TDD pattern.** They are specified in the companion file `2026-06-24-firstclub-membership-part2.md` to keep each document readable. Build order and the exact produced interfaces are fixed here so tasks can be executed independently:
>
> - **Task 4 — User & order-stats foundation + StripedLock.** `User(id,name,cohort)`, `UserRepository`; `UserOrderStats(id, user(unique), totalOrders:long, monthlyOrderValue:BigDecimal, statsMonth:String "YYYY-MM", @Version)`, `UserOrderStatsRepository.findByUserId`; `StripedLock.withLock(long key, Supplier/Runnable)`. Seed 2 demo users.
> - **Task 5 — Subscription core.** `SubscriptionStatus{PENDING,ACTIVE,EXPIRED,CANCELLED}` with `canTransitionTo`; `Subscription(user, plan, purchasedTier, earnedTier(nullable), effectiveTier, status, startAt, expiresAt, idempotencyKey(unique), @Version)`; `PaymentGateway.charge(userId, amount, idemKey)` + `MockPaymentGateway`; `MovementKind`, `TierMovementLog`; `SubscriptionService.subscribe/getCurrent`; idempotency via key + one-ACTIVE-per-user under `StripedLock`; logs `SUBSCRIBE`. `GlobalExceptionHandler` introduced here.
> - **Task 6 — Manual upgrade/downgrade + cancel.** `SubscriptionService.changeTier(userId,targetTier)` (charges on upgrade, recomputes `effectiveTier=MAX`, logs `UPGRADE`/`DOWNGRADE`), `cancel(userId)` (logs `CANCEL`). Effective-tier helper unit-tested.
> - **Task 7 — Order ingestion + domain event.** `DomainEventPublisher` port + `SpringDomainEventPublisher`; `OrderIngestedEvent(userId)`; `OrderEvent(orderId unique,...)`; `OrderEventService.ingest` (idempotent by orderId, updates stats under `StripedLock`, publishes after save); `POST /api/order-events`. Tests: stats update + duplicate no-op.
> - **Task 8 — Eligibility engine + async evaluation.** `RuleType{ORDER_COUNT,ORDER_VALUE,COHORT}`, `EligibilityRule(tier, ruleType, params)`; `EligibilityEvaluator{ruleType(); matches(stats,user,params)}` + 3 impls; `TierEvaluationService.evaluate(userId, allowDemotion)` (highest-rank tier whose rules all pass → earnedTier; promote-only unless allowDemotion; updates effective + logs `EARNED_PROMOTION`/`EARNED_DEMOTION`); `@TransactionalEventListener(AFTER_COMMIT) @Async` listener; `POST /api/users/{id}/tier-evaluation`; `AsyncConfig`. Seed rules. Tests: progression after orders.
> - **Task 9 — `GET /api/users/{id}/benefits`** → `BenefitService.effectiveBenefits` over the active subscription's effective tier.
> - **Task 10 — `GET /api/users/{id}/subscription/history`** → `TierMovementLogRepository.findByUserIdOrderByAtAsc`, `MovementResponse`.
> - **Task 11 — Scheduled monthly sweep + concurrency test.** `SchedulingConfig`, `TierSweepJob` (`evaluate(userId, allowDemotion=true)` for active subs); concurrency test fires N parallel order events + a manual upgrade for one user and asserts correct final effective tier, no lost updates, no double-processing.
> - **Task 12 — README + demo flow.** `curl` walkthrough: subscribe → ingest orders → auto-promote → read benefits → read history.

---

## Self-Review

**Spec coverage:** plans/pricing → T2; benefits configurable → T3; get plans+tiers → T2/T3; subscribe → T5; upgrade/downgrade/cancel → T6; track membership+expiry → T5; tier criteria (orders/value/cohort) → T8; effective-tier MAX → T5/T6/T8; idempotency → T5/T7; concurrency (locks, @Version, async, sweep) → T7/T8/T11; audit log → T5/T6/T8/T10; error handling → T5+; running/demo → T1/T12. No gaps.

**Placeholder scan:** the only forward-reference is the deliberate `ResolvedBenefit` stub in T2, fully realized in T3 — not a placeholder. Tasks 4–12 carry fixed interface contracts above; their step-level code lives in part 2.

**Type consistency:** `effectiveTier`/`earnedTier`/`purchasedTier`, `StripedLock.withLock`, `evaluate(userId, allowDemotion)`, `BenefitHandlerRegistry.resolve(Benefit, overrides)`, repository method names (`findByPlanType`, `findByName`, `findByTierId`, `findByUserId`) are consistent across tasks.
