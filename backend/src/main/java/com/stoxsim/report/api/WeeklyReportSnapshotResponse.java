package com.stoxsim.report.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WeeklyReportSnapshotResponse(
    String version,
    LocalDate periodStart,
    LocalDate periodEnd,
    List<WeeklyMarketReportResponse> markets,
    List<String> learningNotes,
    String disclaimer,
    Instant generatedAt
) {
}
