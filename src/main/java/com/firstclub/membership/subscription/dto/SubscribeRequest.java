package com.firstclub.membership.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscribeRequest(@NotBlank String planType, String tier) {}
