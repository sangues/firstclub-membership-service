package com.firstclub.membership.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeTierRequest(@NotBlank String targetTier) {}
