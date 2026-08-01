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
    public static final ZoneId UNITED_STATES_ZONE = ZoneId.of("America/New_York");

    private static final LocalTime INDIA_PRE_OPEN_START = LocalTime.of(9, 0);
    private static final LocalTime INDIA_PRE_OPEN_MATCHING = LocalTime.of(9, 8);
    private static final LocalTime INDIA_PRE_OPEN_BUFFER = LocalTime.of(9, 12);
    private static final LocalTime INDIA_REGULAR_OPEN = LocalTime.of(9, 15);
    private static final LocalTime INDIA_REGULAR_CLOSE = LocalTime.of(15, 30);

    private static final LocalTime US_PRE_MARKET_OPEN = LocalTime.of(4, 0);
    private static final LocalTime US_REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime US_REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime US_AFTER_HOURS_CLOSE = LocalTime.of(20, 0);

    private final Clock clock;
    private final MarketHolidayRepository holidays;

    public IndiaMarketSessionService(Clock clock, MarketHolidayRepository holidays) {
        this.clock = clock;
        this.holidays = holidays;
    }

    public MarketSessionSnapshot current(MarketExchange exchange) {
        ZoneId zone = isUnitedStatesExchange(exchange) ? UNITED_STATES_ZONE : INDIA_ZONE;
        return at(exchange, ZonedDateTime.ofInstant(clock.instant(), zone));
    }

    public MarketSessionSnapshot at(MarketExchange exchange, ZonedDateTime input) {
        return isUnitedStatesExchange(exchange)
            ? unitedStatesAt(exchange, input)
            : indiaAt(exchange, input);
    }

    private MarketSessionSnapshot indiaAt(MarketExchange exchange, ZonedDateTime input) {
        if (exchange != MarketExchange.NSE && exchange != MarketExchange.BSE) {
            throw new IllegalArgumentException("India market session supports NSE and BSE");
        }
        ZonedDateTime now = input.withZoneSameInstant(INDIA_ZONE);
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        if (!isTradingDay(exchange, date)) {
            LocalDate next = nextTradingDay(exchange, date.plusDays(1));
            return snapshot(
                exchange,
                MarketPhase.HOLIDAY,
                now,
                next.atTime(INDIA_PRE_OPEN_START).atZone(INDIA_ZONE),
                next,
                INDIA_ZONE
            );
        }
        if (time.isBefore(INDIA_PRE_OPEN_START)) {
            return snapshot(
                exchange,
                MarketPhase.AFTER_MARKET,
                now,
                date.atTime(INDIA_PRE_OPEN_START).atZone(INDIA_ZONE),
                date,
                INDIA_ZONE
            );
        }
        if (time.isBefore(INDIA_PRE_OPEN_MATCHING)) {
            return snapshot(
                exchange,
                MarketPhase.PRE_OPEN_ORDER_ENTRY,
                now,
                date.atTime(INDIA_PRE_OPEN_MATCHING).atZone(INDIA_ZONE),
                date,
                INDIA_ZONE
            );
        }
        if (time.isBefore(INDIA_PRE_OPEN_BUFFER)) {
            return snapshot(
                exchange,
                MarketPhase.PRE_OPEN_MATCHING,
                now,
                date.atTime(INDIA_PRE_OPEN_BUFFER).atZone(INDIA_ZONE),
                date,
                INDIA_ZONE
            );
        }
        if (time.isBefore(INDIA_REGULAR_OPEN)) {
            return snapshot(
                exchange,
                MarketPhase.PRE_OPEN_BUFFER,
                now,
                date.atTime(INDIA_REGULAR_OPEN).atZone(INDIA_ZONE),
                date,
                INDIA_ZONE
            );
        }
        if (time.isBefore(INDIA_REGULAR_CLOSE)) {
            return snapshot(
                exchange,
                MarketPhase.REGULAR,
                now,
                date.atTime(INDIA_REGULAR_CLOSE).atZone(INDIA_ZONE),
                date,
                INDIA_ZONE
            );
        }

        LocalDate next = nextTradingDay(exchange, date.plusDays(1));
        return snapshot(
            exchange,
            MarketPhase.AFTER_MARKET,
            now,
            next.atTime(INDIA_PRE_OPEN_START).atZone(INDIA_ZONE),
            next,
            INDIA_ZONE
        );
    }

    private MarketSessionSnapshot unitedStatesAt(
        MarketExchange exchange,
        ZonedDateTime input
    ) {
        ZonedDateTime now = input.withZoneSameInstant(UNITED_STATES_ZONE);
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        if (!isTradingDay(exchange, date)) {
            LocalDate next = nextTradingDay(exchange, date.plusDays(1));
            return snapshot(
                exchange,
                MarketPhase.HOLIDAY,
                now,
                next.atTime(US_PRE_MARKET_OPEN).atZone(UNITED_STATES_ZONE),
                next,
                UNITED_STATES_ZONE
            );
        }
        if (time.isBefore(US_PRE_MARKET_OPEN)) {
            return snapshot(
                exchange,
                MarketPhase.AFTER_MARKET,
                now,
                date.atTime(US_PRE_MARKET_OPEN).atZone(UNITED_STATES_ZONE),
                date,
                UNITED_STATES_ZONE
            );
        }
        if (time.isBefore(US_REGULAR_OPEN)) {
            return snapshot(
                exchange,
                MarketPhase.PRE_MARKET,
                now,
                date.atTime(US_REGULAR_OPEN).atZone(UNITED_STATES_ZONE),
                date,
                UNITED_STATES_ZONE
            );
        }
        if (time.isBefore(US_REGULAR_CLOSE)) {
            return snapshot(
                exchange,
                MarketPhase.REGULAR,
                now,
                date.atTime(US_REGULAR_CLOSE).atZone(UNITED_STATES_ZONE),
                date,
                UNITED_STATES_ZONE
            );
        }
        if (time.isBefore(US_AFTER_HOURS_CLOSE)) {
            return snapshot(
                exchange,
                MarketPhase.AFTER_HOURS,
                now,
                date.atTime(US_AFTER_HOURS_CLOSE).atZone(UNITED_STATES_ZONE),
                date,
                UNITED_STATES_ZONE
            );
        }

        LocalDate next = nextTradingDay(exchange, date.plusDays(1));
        return snapshot(
            exchange,
            MarketPhase.AFTER_MARKET,
            now,
            next.atTime(US_PRE_MARKET_OPEN).atZone(UNITED_STATES_ZONE),
            next,
            UNITED_STATES_ZONE
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

    public boolean isUnitedStatesExchange(MarketExchange exchange) {
        return exchange == MarketExchange.NASDAQ
            || exchange == MarketExchange.NYSE
            || exchange == MarketExchange.NYSE_ARCA
            || exchange == MarketExchange.AMEX
            || exchange == MarketExchange.CBOE;
    }

    private MarketSessionSnapshot snapshot(
        MarketExchange exchange,
        MarketPhase phase,
        ZonedDateTime now,
        ZonedDateTime nextTransition,
        LocalDate orderDate,
        ZoneId zone
    ) {
        return new MarketSessionSnapshot(
            exchange,
            phase,
            zone.getId(),
            now,
            nextTransition,
            orderDate
        );
    }
}
