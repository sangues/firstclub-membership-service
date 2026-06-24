package com.firstclub.membership.order.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderEventRequest(
        @NotBlank String orderId,
        @NotNull Long userId,
        @NotNull @Positive BigDecimal amount,
        Instant occurredAt) {}
