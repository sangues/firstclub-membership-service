package com.firstclub.membership.order;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserOrderStatsRepository extends JpaRepository<UserOrderStats, Long> {
    Optional<UserOrderStats> findByUserId(Long userId);
}
