package com.stoxsim.calendar.service;

import java.time.Clock;
import java.time.DayOfWeek;
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

    private static final LocalTime PRE_OPEN_START = LocalTime.of(9, 0);
    private static final LocalTime PRE_OPEN_MATCHING = LocalTime.of(9, 8);
    private static final LocalTime PRE_OPEN_BUFFER = LocalTime.of(9, 12);
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 15);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);

    private final Clock clock;
    private final MarketHolidayRepository holidays;

    public IndiaMarketSessionService(Clock clock, MarketHolidayRepository holidays) {
        this.clock = clock;
        this.holidays = holidays;
    }

    public MarketSessionSnapshot current(MarketExchange exchange) {
        return at(exchange, ZonedDateTime.ofInstant(clock.instant(), INDIA_ZONE));
    }

    public MarketSessionSnapshot at(MarketExchange exchange, ZonedDateTime input) {
        ZonedDateTime now = input.withZoneSameInstant(INDIA_ZONE);
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        if (!isTradingDay(exchange, date)) {
            LocalDate next = nextTradingDay(exchange, date.plusDays(1));
            return snapshot(
                exchange,
                MarketPhase.HOLIDAY,
                now,
                next.atTime(PRE_OPEN_START).atZone(INDIA_ZONE),
                next
            );
        }
        if (time.isBefore(PRE_OPEN_START)) {
            return snapshot(
                exchange,
                MarketPhase.AFTER_MARKET,
                now,
                date.atTime(PRE_OPEN_START).atZone(INDIA_ZONE),
                date
            );
        }
        if (time.isBefore(PRE_OPEN_MATCHING)) {
            return snapshot(
                exchange,
                MarketPhase.PRE_OPEN_ORDER_ENTRY,
                now,
                date.atTime(PRE_OPEN_MATCHING).atZone(INDIA_ZONE),
                date
            );
        }
        if (time.isBefore(PRE_OPEN_BUFFER)) {
            return snapshot(
                exchange,
                MarketPhase.PRE_OPEN_MATCHING,
                now,
                date.atTime(PRE_OPEN_BUFFER).atZone(INDIA_ZONE),
                date
            );
        }
        if (time.isBefore(REGULAR_OPEN)) {
            return snapshot(
                exchange,
                MarketPhase.PRE_OPEN_BUFFER,
                now,
                date.atTime(REGULAR_OPEN).atZone(INDIA_ZONE),
                date
            );
        }
        if (time.isBefore(REGULAR_CLOSE)) {
            return snapshot(
                exchange,
                MarketPhase.REGULAR,
                now,
                date.atTime(REGULAR_CLOSE).atZone(INDIA_ZONE),
                date
            );
        }

        LocalDate next = nextTradingDay(exchange, date.plusDays(1));
        return snapshot(
            exchange,
            MarketPhase.AFTER_MARKET,
            now,
            next.atTime(PRE_OPEN_START).atZone(INDIA_ZONE),
            next
        );
    }

    public boolean isTradingDay(MarketExchange exchange, LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY
            && day != DayOfWeek.SUNDAY
            && !holidays.existsByExchangeAndHolidayDate(exchange, date);
    }

    public LocalDate nextTradingDay(MarketExchange exchange, LocalDate from) {
        LocalDate candidate = from;
        for (int attempts = 0; attempts < 370; attempts++) {
            if (isTradingDay(exchange, candidate)) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        throw new IllegalStateException("Could not resolve the next trading day");
    }

    private MarketSessionSnapshot snapshot(
        MarketExchange exchange,
        MarketPhase phase,
        ZonedDateTime now,
        ZonedDateTime nextTransition,
        LocalDate orderDate
    ) {
        return new MarketSessionSnapshot(
            exchange,
            phase,
            INDIA_ZONE.getId(),
            now,
            nextTransition,
            orderDate
        );
    }
}
