package com.firstclub.membership.catalog;

import com.firstclub.membership.common.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "benefit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Benefit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BenefitType type;
    @Column(nullable = false)
    private String description;
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "params", length = 2000)
    private Map<String, String> params = new HashMap<>();
}
