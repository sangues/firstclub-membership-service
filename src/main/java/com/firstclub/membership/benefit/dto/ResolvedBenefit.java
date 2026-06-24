package com.firstclub.membership.benefit.dto;
import java.util.Map;
public record ResolvedBenefit(String type, String description, Map<String, String> params) {}
