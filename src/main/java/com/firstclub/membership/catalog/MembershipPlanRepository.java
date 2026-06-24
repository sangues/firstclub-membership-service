package com.firstclub.membership.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {
    Optional<MembershipPlan> findByPlanType(PlanType planType);
}
