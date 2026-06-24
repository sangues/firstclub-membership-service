package com.firstclub.membership.benefit;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/benefits")
public class BenefitController {
    private final BenefitService service;
    public BenefitController(BenefitService service) { this.service = service; }

    @GetMapping
    public List<ResolvedBenefit> benefits(@PathVariable Long userId) {
        return service.effectiveBenefits(userId);
    }
}
