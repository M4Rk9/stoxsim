package com.stoxsim.analytics.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CohortOverview(String version, LocalDate from, LocalDate to, String groupBy,
    Instant generatedAt, Instant activityAvailableFrom, Instant journeyAvailableFrom,
    Instant signupDimensionsStartedAt, List<Row> rows) {
    public record Rate(long eligible, long returned, long immature, long excluded, Double percent) { }
    public record Row(String cohort, long registrations, long journeyEligible, long journeyImmature,
        long journeyExcluded, long researched, long watchlisted, long strategyCreated,
        long traded, long reviewed, long linkedJourneyCompleted, Rate d1, Rate d7,
        Rate days1To7, Rate followingWeek) { }
}
