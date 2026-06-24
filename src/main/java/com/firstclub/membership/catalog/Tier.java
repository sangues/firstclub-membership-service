package com.firstclub.membership.catalog;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tier")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Tier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(nullable = false)
    private int rank;
    @Column(nullable = false)
    private boolean active = true;
}
