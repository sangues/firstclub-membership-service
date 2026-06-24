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

            Tier prevEffective = sub.getEffectiveTier();
            sub.setEarnedTier(newEarned);
            sub.recomputeEffectiveTier();
            subscriptions.save(sub);

            Tier nowEffective = sub.getEffectiveTier();
            if (nowEffective.getRank() > prevEffective.getRank())
                subscriptionService.logMovement(user, prevEffective.getName(),
                        nowEffective.getName(), MovementKind.EARNED_PROMOTION);
            else if (nowEffective.getRank() < prevEffective.getRank())
                subscriptionService.logMovement(user, prevEffective.getName(),
                        nowEffective.getName(), MovementKind.EARNED_DEMOTION);
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
}
