package com.firstclub.membership.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TierMovementLogRepository extends JpaRepository<TierMovementLog, Long> {
    List<TierMovementLog> findByUserIdOrderByAtAsc(Long userId);
}
