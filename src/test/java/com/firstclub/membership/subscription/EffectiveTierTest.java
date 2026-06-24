package com.firstclub.membership.subscription;

import com.firstclub.membership.catalog.Tier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EffectiveTierTest {
    private Tier tier(String name, int rank) { return new Tier(null, name, rank, true); }

    @Test
    void effectiveIsHigherOfPurchasedAndEarned() {
        Subscription s = new Subscription();
        s.setPurchasedTier(tier("SILVER", 1));
        s.setEarnedTier(tier("GOLD", 2));
        s.recomputeEffectiveTier();
        assertThat(s.getEffectiveTier().getName()).isEqualTo("GOLD");
    }

    @Test
    void purchasedFloorWinsWhenHigherThanEarned() {
        Subscription s = new Subscription();
        s.setPurchasedTier(tier("PLATINUM", 3));
        s.setEarnedTier(tier("GOLD", 2));
        s.recomputeEffectiveTier();
        assertThat(s.getEffectiveTier().getName()).isEqualTo("PLATINUM");
    }
}
