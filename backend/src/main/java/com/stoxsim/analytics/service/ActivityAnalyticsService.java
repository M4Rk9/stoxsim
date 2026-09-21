package com.stoxsim.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.analytics.api.ActivityOverview;
import com.stoxsim.analytics.api.ActivityOverview.*;
import com.stoxsim.analytics.repository.ActivityRepository;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;

@Service
public class ActivityAnalyticsService {
    private final AppUserRepository users;
    private final ActivityRepository activity;
    private final Clock clock;
    public ActivityAnalyticsService(AppUserRepository users, ActivityRepository activity, Clock clock) {
        this.users = users; this.activity = activity; this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public ActivityOverview overview(UUID requester, LocalDate from, LocalDate to) {
        var user = users.findById(requester).orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (!user.isPlatformAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access is required");
        var now = clock.instant();
        var range = AnalyticsPeriod.of(from, to, now);
        var started = activity.startedAt();
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        var retainedFrom = today.minusDays(ProductActivityService.RETENTION_DAYS - 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var coverage = started.isAfter(retainedFrom) ? started : retainedFrom;
        var daily = activity.daily(range.start().isBefore(coverage) ? coverage : range.start(), range.end())
            .stream().collect(Collectors.toMap(DailyActive::date, DailyActive::learners));
        var trend = range.from().datesUntil(range.to().plusDays(1)).map(day -> new DailyActive(day,
            day.atStartOfDay(ZoneOffset.UTC).toInstant().isBefore(coverage) ? null : daily.getOrDefault(day, 0L))).toList();
        return new ActivityOverview("product-activity-v1", range.from(), range.to(), "UTC", now, started, coverage,
            new ActiveUsers(active(1, range, coverage), active(7, range, coverage), active(30, range, coverage)), trend,
            activity.activation(range.start(), range.end(), coverage, now),
            List.of(1, 7, 30).stream().map(day -> activity.retention(day, range.start(), range.end(), coverage, today)).toList());
    }

    private Long active(int days, AnalyticsPeriod range, Instant coverage) {
        var start = range.to().minusDays(days - 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return start.isBefore(coverage) ? null : activity.active(start, range.end());
    }
}
