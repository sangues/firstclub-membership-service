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
