package com.stoxsim.report.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.report.domain.WeeklyReportPreference;
import com.stoxsim.report.service.WeeklyReportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reports/weekly")
public class WeeklyReportController {

    private final WeeklyReportService reports;

    public WeeklyReportController(WeeklyReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/preferences")
    public WeeklyReportPreferenceResponse preference(@AuthenticationPrincipal Jwt jwt) {
        return reports.preference(userId(jwt));
    }

    @PutMapping("/preferences")
    public WeeklyReportPreferenceResponse updatePreference(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody WeeklyReportPreferenceRequest request
    ) {
        return reports.updatePreference(userId(jwt), request.enabled(), request.zoneId());
    }

    @GetMapping
    public List<WeeklyPortfolioReportResponse> reportHistory(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return reports.reports(userId(jwt));
    }

    @GetMapping("/preview")
    public WeeklyReportSnapshotResponse preview(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = WeeklyReportPreference.DEFAULT_ZONE_ID) String zoneId
    ) {
        return reports.preview(userId(jwt), zoneId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
