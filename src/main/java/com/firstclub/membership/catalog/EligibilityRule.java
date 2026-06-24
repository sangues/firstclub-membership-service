package com.firstclub.membership.catalog;

import com.firstclub.membership.common.JsonMapConverter;
import com.firstclub.membership.tier.RuleType;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "eligibility_rule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EligibilityRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "tier_id")
    private Tier tier;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private RuleType ruleType;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "params", length = 2000)
    private Map<String, String> params = new HashMap<>();
}
