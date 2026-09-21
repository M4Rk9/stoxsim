package com.stoxsim.analytics.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsOverview(
    String version,
    LocalDate from,
    LocalDate to,
    String timeZone,
    Instant generatedAt,
    UserTotals users,
    SignupCohort cohort,
    List<DailySignups> signups,
    List<OrderCount> orders
) {
    public record UserTotals(long registered, long emailVerified) { }
    public record SignupCohort(long registered, long firstTradeCompleted, Double firstTradePercent) { }
    public record DailySignups(LocalDate date, long registered) { }
    public record OrderCount(String marketRegion, String status, long count) { }
}
