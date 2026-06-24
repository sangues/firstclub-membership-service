package com.firstclub.membership.catalog;

import com.firstclub.membership.catalog.dto.BenefitResponse;
import com.firstclub.membership.catalog.dto.PlanResponse;
import com.firstclub.membership.catalog.dto.TierResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/plans")
    public List<PlanResponse> plans() { return catalog.listPlans(); }

    @GetMapping("/tiers")
    public List<TierResponse> tiers() { return catalog.listTiers(); }

    @GetMapping("/benefits")
    public List<BenefitResponse> benefits() { return catalog.listBenefits(); }
}
