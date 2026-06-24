package com.firstclub.membership.subscription.payment;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {
    @Override
    public PaymentResult charge(Long userId, BigDecimal amount, String idempotencyKey) {
        return new PaymentResult(true, "MOCK-" + UUID.randomUUID());
    }
}
