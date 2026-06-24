package com.firstclub.membership.subscription.payment;

import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentResult charge(Long userId, BigDecimal amount, String idempotencyKey);
}
