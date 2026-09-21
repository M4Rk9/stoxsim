package com.stoxsim.analytics.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ActivityOverview(
    String version, LocalDate from, LocalDate to, String timeZone, Instant generatedAt,
    Instant trackingStartedAt, Instant availableFrom, ActiveUsers activeUsers,
    List<DailyActive> daily, Activation activation, List<Retention> retention
) {
    public record ActiveUsers(Long dau, Long wau, Long mau) { }
    public record DailyActive(LocalDate date, Long learners) { }
    public record Activation(long eligible, long immature, long excluded, long researched,
        long watchlisted, long activated, long executed, Double percent, Double medianHours) { }
    public record Retention(int day, long eligible, long returned, long immature, long excluded, Double percent) { }
}
