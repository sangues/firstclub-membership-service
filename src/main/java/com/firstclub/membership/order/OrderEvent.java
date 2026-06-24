package com.firstclub.membership.order;

import com.firstclub.membership.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_event")
@Getter @Setter @NoArgsConstructor
public class OrderEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String orderId;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private Instant occurredAt;
    @Column(nullable = false)
    private boolean processed;
}
