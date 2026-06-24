package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BenefitHandlerRegistryTest {
    @Autowired BenefitHandlerRegistry registry;

    @Test
    void overrideParamsWinOverBenefitDefaults() {
        Benefit b = new Benefit(1L, BenefitType.PERCENT_DISCOUNT, "Discount", Map.of("discountPercent", "5"));
        ResolvedBenefit r = registry.resolve(b, Map.of("discountPercent", "15"));
        assertThat(r.type()).isEqualTo("PERCENT_DISCOUNT");
        assertThat(r.params().get("discountPercent")).isEqualTo("15");
    }
}
