package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import com.firstclub.membership.catalog.Benefit;
import com.firstclub.membership.catalog.BenefitType;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class BenefitHandlerRegistry {
    private final Map<BenefitType, BenefitHandler> handlers = new EnumMap<>(BenefitType.class);

    public BenefitHandlerRegistry(List<BenefitHandler> all) {
        for (BenefitHandler h : all) handlers.put(h.type(), h);
    }

    public ResolvedBenefit resolve(Benefit benefit, Map<String, String> overrides) {
        Map<String, String> merged = new HashMap<>(benefit.getParams());
        if (overrides != null) merged.putAll(overrides);
        BenefitHandler handler = handlers.get(benefit.getType());
        if (handler == null) throw new IllegalStateException("No handler for " + benefit.getType());
        return handler.resolve(benefit, merged);
    }
}
