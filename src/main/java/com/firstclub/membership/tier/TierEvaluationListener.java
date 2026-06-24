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
