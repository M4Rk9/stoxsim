package com.stoxsim.report.api;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.report.domain.WeeklyReportDeliveryStatus;

public record WeeklyPortfolioReportResponse(
    UUID id,
    WeeklyReportDeliveryStatus deliveryStatus,
    int deliveryAttempts,
    Instant deliveryAttemptedAt,
    WeeklyReportSnapshotResponse snapshot
) {
}
