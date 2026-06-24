package com.firstclub.membership.catalog;

import com.firstclub.membership.common.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "tier_benefit", uniqueConstraints = @UniqueConstraint(columnNames = {"tier_id", "benefit_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TierBenefit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "tier_id")
    private Tier tier;
    @ManyToOne(optional = false) @JoinColumn(name = "benefit_id")
    private Benefit benefit;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "param_overrides", length = 2000)
    private Map<String, String> paramOverrides = new HashMap<>();
}
