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
