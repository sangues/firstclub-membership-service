package com.firstclub.membership.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EligibilityRuleRepository extends JpaRepository<EligibilityRule, Long> {
    List<EligibilityRule> findByTierId(Long tierId);
}
