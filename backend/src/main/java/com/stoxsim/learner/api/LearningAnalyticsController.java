package com.stoxsim.learner.api;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.analytics.service.AnalyticsPeriod;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;

/** Aggregate observed usage, never an inferred invoice or historical backfill. */
@RestController
@RequestMapping("/api/v1/admin/analytics/learning")
public class LearningAnalyticsController {
    private final AppUserRepository users;
    private final JdbcTemplate db;
    private final Clock clock;
    public LearningAnalyticsController(AppUserRepository users, JdbcTemplate db, Clock clock) {
        this.users = users; this.db = db; this.clock = clock;
    }
    public record Overview(String version, LocalDate from, LocalDate to, String timeZone,
        Instant learningTrackingStartedAt, Instant aiTrackingStartedAt, Instant retainedFrom,
        boolean fullLearningWindowObserved, boolean fullAiWindowObserved,
        Long recommendationLearners, Long reflectionLearners, long providerAttempts,
        long providerReportedTotalTokens, long responsesWithTokenUsage, String costStatus) { }

    @GetMapping
    @Transactional(readOnly = true, timeout = 10)
    public Overview overview(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        var user = users.findById(UUID.fromString(jwt.getSubject()))
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (!user.isPlatformAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access is required");
        var period = AnalyticsPeriod.of(date(from), date(to), clock.instant());
        var coverage = db.queryForObject("SELECT learner_usage_started_at FROM analytics_coverage WHERE singleton", Timestamp.class).toInstant();
        var aiCoverage = db.queryForObject("SELECT finwiz_usage_started_at FROM analytics_coverage WHERE singleton", Timestamp.class).toInstant();
        var retained = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(179).atStartOfDay(ZoneOffset.UTC).toInstant();
        boolean learningComplete = !period.start().isBefore(coverage) && !period.start().isBefore(retained);
        boolean aiComplete = !period.start().isBefore(aiCoverage) && !period.start().isBefore(retained);
        long[] ai = db.queryForObject("""
            SELECT coalesce(sum(provider_attempts),0),coalesce(sum(reported_total_tokens),0),coalesce(sum(responses_with_usage),0)
            FROM finwiz_global_usage_daily WHERE usage_day>=? AND usage_day<=?
            """, (rs, row) -> new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3)}, Date.valueOf(period.from()), Date.valueOf(period.to()));
        return new Overview("learning-usage-v1", period.from(), period.to(), "UTC", coverage, aiCoverage, retained,
            learningComplete, aiComplete, learningComplete ? count("RECOMMENDATION_USED", period) : null,
            learningComplete ? count("REFLECTION_SAVED", period) : null, ai[0], ai[1], ai[2],
            "Observed usage only, including failed attempts. Provider pricing, free quota and invoices must be verified separately. Check coverage flags for incomplete windows.");
    }
    private long count(String event, AnalyticsPeriod period) {
        return db.queryForObject("""
            SELECT count(DISTINCT a.user_id) FROM product_activity a JOIN app_user u ON u.id=a.user_id
            WHERE u.platform_role='USER' AND NOT u.is_test_account AND a.event_name=? AND a.first_at>=? AND a.first_at<?
            """, Long.class, event, Timestamp.from(period.start()), Timestamp.from(period.end()));
    }
    private LocalDate date(String value) {
        try { return value == null ? null : LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates must use YYYY-MM-DD"); }
    }
}
