package com.stoxsim.report.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WeeklyReportPreferenceRequest(
    boolean enabled,
    @NotBlank @Size(max = 64) String zoneId
) {
}
