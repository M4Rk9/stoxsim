package com.stoxsim.calendar.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import com.stoxsim.calendar.domain.MarketPhase;
import com.stoxsim.calendar.repository.MarketHolidayRepository;
import com.stoxsim.instrument.domain.MarketExchange;

@Service
public class IndiaMarketSessionService {
    public static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
    public static final ZoneId UNITED_STATES_ZONE = ZoneId.of("America/New_York");
    private final Clock clock;
    private final MarketHolidayRepository holidays;
    private final VerifiedMarketCalendar calendar = new VerifiedMarketCalendar();

    public IndiaMarketSessionService(Clock clock, MarketHolidayRepository holidays) {
        this.clock = clock;
        this.holidays = holidays;
    }

    private ZoneId zone(MarketExchange exchange) {
        return isUnitedStatesExchange(exchange) ? UNITED_STATES_ZONE : INDIA_ZONE;
    }

    public MarketSessionSnapshot current(MarketExchange exchange) {
        return at(exchange, ZonedDateTime.ofInstant(clock.instant(), zone(exchange)));
    }

    public MarketSessionSnapshot at(MarketExchange exchange, ZonedDateTime input) {
        var now = input.withZoneSameInstant(zone(exchange));
        var date = now.toLocalDate();
        var time = now.toLocalTime();
        var day = calendar.day(exchange, date);
        if (!day.known()) return unavailable(exchange, now);
        if (!isTradingDay(exchange, date)) return nextSession(exchange, now, MarketPhase.HOLIDAY);
        boolean us = isUnitedStatesExchange(exchange);
        var preOpen = us ? LocalTime.of(4, 0) : day.preOpen();
        if (time.isBefore(preOpen)) return snapshot(exchange, MarketPhase.AFTER_MARKET, now, date, preOpen, date);
        if (us) {
            if (time.isBefore(day.open())) return snapshot(exchange, MarketPhase.PRE_MARKET, now, date, day.open(), date);
        } else {
            if (time.isBefore(day.matching())) return snapshot(exchange, MarketPhase.PRE_OPEN_ORDER_ENTRY, now, date, day.matching(), date);
            if (time.isBefore(day.buffer())) return snapshot(exchange, MarketPhase.PRE_OPEN_MATCHING, now, date, day.buffer(), date);
            if (time.isBefore(day.open())) return snapshot(exchange, MarketPhase.PRE_OPEN_BUFFER, now, date, day.open(), date);
        }
        if (time.isBefore(day.continuousClose())) return snapshot(exchange, MarketPhase.REGULAR, now, date, day.continuousClose(), date);
        if (time.isBefore(day.close())) return snapshot(exchange, MarketPhase.CLOSING_AUCTION, now, date, day.close(), date);
        // DAY orders entered after the core close belong to the next session, including US after-hours.
        var next = nextSession(exchange, now, MarketPhase.AFTER_MARKET);
        if (us && time.isBefore(day.extendedClose()) && next.phase() != MarketPhase.CALENDAR_UNAVAILABLE)
            return snapshot(exchange, MarketPhase.AFTER_HOURS, now, date, day.extendedClose(), next.orderDate());
        return next;
    }

    private MarketSessionSnapshot nextSession(MarketExchange exchange, ZonedDateTime now, MarketPhase phase) {
        var next = nextTradingDay(exchange, now.toLocalDate().plusDays(1));
        if (next == null) return unavailable(exchange, now);
        var start = isUnitedStatesExchange(exchange) ? LocalTime.of(4, 0) : calendar.day(exchange, next).preOpen();
        return snapshot(exchange, phase, now, next, start, next);
    }

    public boolean isCalendarKnown(MarketExchange exchange, LocalDate date) {
        return calendar.day(exchange, date).known();
    }

    public boolean isTradingDay(MarketExchange exchange, LocalDate date) {
        return calendar.day(exchange, date).trading() && !holidays.existsByExchangeAndHolidayDate(exchange, date);
    }

    /** Null means an unverified date was reached; never silently skip an unknown special session. */
    public LocalDate nextTradingDay(MarketExchange exchange, LocalDate from) {
        for (var candidate = from; candidate.isBefore(from.plusDays(370)); candidate = candidate.plusDays(1)) {
            if (!isCalendarKnown(exchange, candidate)) return null;
            if (isTradingDay(exchange, candidate)) return candidate;
        }
        return null;
    }

    /** Rechecked under the order lock so an expiry scan cannot expire next-session orders. */
    public boolean hasSessionEnded(MarketExchange exchange, LocalDate orderDate) {
        var now = clock.instant().atZone(zone(exchange));
        if (orderDate.isBefore(now.toLocalDate())) return true;
        if (orderDate.isAfter(now.toLocalDate())) return false;
        var day = calendar.day(exchange, orderDate);
        return day.known() && (!isTradingDay(exchange, orderDate) || !now.toLocalTime().isBefore(day.close()));
    }

    public boolean isUnitedStatesExchange(MarketExchange exchange) {
        return exchange == MarketExchange.NASDAQ || exchange == MarketExchange.NYSE
            || exchange == MarketExchange.NYSE_ARCA || exchange == MarketExchange.AMEX || exchange == MarketExchange.CBOE;
    }

    private MarketSessionSnapshot unavailable(MarketExchange exchange, ZonedDateTime now) {
        return new MarketSessionSnapshot(exchange, MarketPhase.CALENDAR_UNAVAILABLE, now.getZone().getId(), now, null, null);
    }

    private MarketSessionSnapshot snapshot(MarketExchange exchange, MarketPhase phase, ZonedDateTime now,
        LocalDate transitionDate, LocalTime transitionTime, LocalDate orderDate) {
        return new MarketSessionSnapshot(exchange, phase, now.getZone().getId(), now,
            transitionDate.atTime(transitionTime).atZone(now.getZone()), orderDate);
    }
}
