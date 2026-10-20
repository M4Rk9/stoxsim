package com.stoxsim.calendar.service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Properties;

import com.stoxsim.instrument.domain.MarketExchange;

/** Reviewed exchange dates. Missing coverage is never interpreted as an open market. */
final class VerifiedMarketCalendar {
    record Day(boolean known, LocalTime open, LocalTime close, LocalTime extendedClose, LocalTime preOpen, LocalTime matching, LocalTime buffer, LocalTime continuousClose) {
        boolean trading() { return known && open != null; }
    }
    private final Properties data;

    VerifiedMarketCalendar() {
        data = new Properties();
        try (var stream = getClass().getResourceAsStream("/market-calendar.properties")) {
            if (stream == null) throw new IllegalStateException("Missing market calendar");
            data.load(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read market calendar", e);
        }
    }

    Day day(MarketExchange exchange, LocalDate date) {
        String group = exchange == MarketExchange.NSE || exchange == MarketExchange.BSE ? "INDIA" : "US";
        String coverage = data.getProperty(group + ".coverage", "");
        if (!coverage.contains("|")) return new Day(false, null, null, null, null, null, null, null);
        String[] bounds = coverage.split("\\|");
        if (date.isBefore(LocalDate.parse(bounds[0])) || date.isAfter(LocalDate.parse(bounds[1])))
            return new Day(false, null, null, null, null, null, null, null);
        String exception = data.getProperty(group + "." + date);
        if ("UNKNOWN".equals(exception)) return new Day(false, null, null, null, null, null, null, null);
        if ("CLOSED".equals(exception)) return new Day(true, null, null, null, null, null, null, null);
        if (exception == null && (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY))
            return new Day(true, null, null, null, null, null, null, null);
        String[] hours = (exception == null ? data.getProperty(group + ".hours") : exception).split("\\|");
        LocalTime open = LocalTime.parse(hours[0]), close = LocalTime.parse(hours[1]);
        LocalTime extended = hours.length > 2 ? LocalTime.parse(hours[2]) : close;
        if (!open.isBefore(close) || extended.isBefore(close)) throw new IllegalStateException("Invalid market hours: " + date);
        LocalTime preOpen = hours.length > 3 ? LocalTime.parse(hours[3]) : open.minusMinutes(15);
        LocalTime matching = hours.length > 4 ? LocalTime.parse(hours[4]) : open.minusMinutes(7);
        LocalTime buffer = hours.length > 5 ? LocalTime.parse(hours[5]) : open.minusMinutes(3);
        LocalTime continuousClose = hours.length > 6 ? LocalTime.parse(hours[6]) : close;
        if (!preOpen.isBefore(matching) || !matching.isBefore(buffer) || !buffer.isBefore(open)
            || !open.isBefore(continuousClose) || continuousClose.isAfter(close))
            throw new IllegalStateException("Invalid market phases: " + date);
        return new Day(true, open, close, extended, preOpen, matching, buffer, continuousClose);
    }
}
