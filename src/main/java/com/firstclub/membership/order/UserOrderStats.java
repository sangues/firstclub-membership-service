package com.firstclub.membership.order;

import com.firstclub.membership.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "user_order_stats")
@Getter @Setter @NoArgsConstructor
public class UserOrderStats {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "user_id", unique = true)
    private User user;
    @Column(nullable = false)
    private long totalOrders = 0;
    @Column(nullable = false)
    private BigDecimal monthlyOrderValue = BigDecimal.ZERO;
    @Column
    private String statsMonth; // "YYYY-MM"
    @Version
    private Long version;

    public UserOrderStats(User user) { this.user = user; }
}
