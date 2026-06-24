package com.firstclub.membership.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);
    Optional<Subscription> findByIdempotencyKey(String idempotencyKey);
    List<Subscription> findAllByStatus(SubscriptionStatus status);
}
