package com.firstclub.membership.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    boolean existsByOrderId(String orderId);
}
