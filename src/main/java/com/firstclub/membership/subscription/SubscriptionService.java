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
