package com.stoxsim.analytics.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.analytics.service.ActivityAnalyticsService;

@RestController
@RequestMapping("/api/v1/admin/analytics/activity")
public class ActivityAnalyticsController {
    private final ActivityAnalyticsService analytics;
    public ActivityAnalyticsController(ActivityAnalyticsService analytics) { this.analytics = analytics; }
    @GetMapping
    public ResponseEntity<ActivityOverview> overview(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(analytics.overview(UUID.fromString(jwt.getSubject()), date(from), date(to)));
    }
    private LocalDate date(String value) {
        try { return value == null ? null : LocalDate.parse(value); }
        catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates must use YYYY-MM-DD");
        }
    }
}
