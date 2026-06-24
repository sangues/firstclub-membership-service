package com.firstclub.membership.subscription;

import com.firstclub.membership.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "tier_movement_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TierMovementLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(name = "from_tier")
    private String fromTier;
    @Column(name = "to_tier")
    private String toTier;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private MovementKind kind;
    @Column(nullable = false)
    private Instant at;
}
