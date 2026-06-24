package com.firstclub.membership.catalog;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "membership_plan")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MembershipPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PlanType planType;
    @Column(nullable = false)
    private BigDecimal price;
    @Column(nullable = false)
    private int durationDays;
    @Column(nullable = false)
    private boolean active = true;
}
