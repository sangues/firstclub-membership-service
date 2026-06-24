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
