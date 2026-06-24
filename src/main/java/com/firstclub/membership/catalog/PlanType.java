package com.firstclub.membership.catalog;

public enum PlanType {
    MONTHLY(30), QUARTERLY(90), YEARLY(365);
    private final int durationDays;
    PlanType(int durationDays) { this.durationDays = durationDays; }
    public int durationDays() { return durationDays; }
}
