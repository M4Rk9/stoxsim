package com.stoxsim.analytics.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.analytics.api.AnalyticsOverview;
import com.stoxsim.analytics.api.AnalyticsOverview.DailySignups;
import com.stoxsim.analytics.repository.AnalyticsRepository;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;

@Service
public class AnalyticsService {
    private final AppUserRepository users;
    private final AnalyticsRepository analytics;
    private final Clock clock;

    public AnalyticsService(AppUserRepository users, AnalyticsRepository analytics, Clock clock) {
        this.users = users;
        this.analytics = analytics;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public AnalyticsOverview overview(UUID requester, LocalDate from, LocalDate to) {
        // Read the database role on every request; never trust browser state or a stale JWT role.
        var user = users.findById(requester)
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (!user.isPlatformAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access is required");
        }

        var now = clock.instant();
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        var last = to == null ? today : to;
        if (last.isBefore(LocalDate.of(1970, 1, 1)) || last.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose 1–90 inclusive UTC dates from 1970 through today");
        }
        var first = from == null ? last.minusDays(29) : from;
        if (first.isBefore(LocalDate.of(1970, 1, 1)) || first.isAfter(last)
            || last.isAfter(today) || ChronoUnit.DAYS.between(first, last) >= 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose 1–90 inclusive UTC dates from 1970 through today");
        }
        var start = first.atStartOfDay(ZoneOffset.UTC).toInstant();
        var end = last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (end.isAfter(now)) end = now;

        var totals = analytics.users(now);
        var cohort = analytics.cohort(start, end, now);
        var daily = analytics.signups(start, end).stream()
            .collect(Collectors.toMap(DailySignups::date, DailySignups::registered));
        var trend = first.datesUntil(last.plusDays(1))
            .map(day -> new DailySignups(day, daily.getOrDefault(day, 0L))).toList();
        return new AnalyticsOverview("owner-analytics-v1", first, last, "UTC", now,
            totals, cohort, trend, analytics.orders(start, end));
    }
}
