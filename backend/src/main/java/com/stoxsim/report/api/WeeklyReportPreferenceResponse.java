package com.stoxsim.report.api;

import java.time.Instant;

public record WeeklyReportPreferenceResponse(
    boolean enabled,
    String zoneId,
    String schedule,
    boolean verifiedEmailRequired,
    Instant updatedAt
) {
}
