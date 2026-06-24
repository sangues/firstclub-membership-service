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
