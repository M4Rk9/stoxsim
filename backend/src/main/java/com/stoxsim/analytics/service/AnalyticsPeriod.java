package com.stoxsim.analytics.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record AnalyticsPeriod(LocalDate from, LocalDate to, Instant start, Instant end) {
    public static AnalyticsPeriod of(LocalDate from, LocalDate to, Instant now) {
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        var last = to == null ? today : to;
        if (last.isBefore(LocalDate.of(1970, 1, 1)) || last.isAfter(today)) throw invalid();
        var first = from == null ? last.minusDays(29) : from;
        if (first.isBefore(LocalDate.of(1970, 1, 1)) || first.isAfter(last)
            || ChronoUnit.DAYS.between(first, last) >= 90) throw invalid();
        var end = last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new AnalyticsPeriod(first, last, first.atStartOfDay(ZoneOffset.UTC).toInstant(), end.isAfter(now) ? now : end);
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose 1–90 inclusive UTC dates from 1970 through today");
    }
}
