package com.firstclub.membership.catalog.dto;

import com.firstclub.membership.benefit.dto.ResolvedBenefit;
import java.util.List;

public record TierResponse(Long id, String name, int rank, List<ResolvedBenefit> benefits) {
}
