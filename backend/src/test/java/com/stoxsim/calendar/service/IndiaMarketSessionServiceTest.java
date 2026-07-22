package com.stoxsim.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stoxsim.calendar.domain.MarketPhase;
import com.stoxsim.calendar.repository.MarketHolidayRepository;
import com.stoxsim.instrument.domain.MarketExchange;

class IndiaMarketSessionServiceTest {

    private IndiaMarketSessionService sessions;

    @BeforeEach
    void setUp() {
        var holidays = mock(MarketHolidayRepository.class);
        when(holidays.existsByExchangeAndHolidayDate(any(), any())).thenReturn(false);
        sessions = new IndiaMarketSessionService(Clock.systemUTC(), holidays);
    }

    @Test
    void mapsIndiaSessionBoundariesDeterministically() {
        assertPhase(9, 5, MarketPhase.PRE_OPEN_ORDER_ENTRY);
        assertPhase(9, 10, MarketPhase.PRE_OPEN_MATCHING);
        assertPhase(9, 14, MarketPhase.PRE_OPEN_BUFFER);
        assertPhase(10, 30, MarketPhase.REGULAR);
        assertPhase(15, 31, MarketPhase.AFTER_MARKET);
    }

    @Test
    void treatsWeekendAsHoliday() {
        var saturday = ZonedDateTime.of(
            2026,
            7,
            25,
            10,
            0,
            0,
            0,
            ZoneId.of("Asia/Kolkata")
        );

        var status = sessions.at(MarketExchange.NSE, saturday);

        assertThat(status.phase()).isEqualTo(MarketPhase.HOLIDAY);
        assertThat(status.orderDate()).isEqualTo("2026-07-27");
    }

    private void assertPhase(int hour, int minute, MarketPhase expected) {
        var time = ZonedDateTime.of(
            2026,
            7,
            22,
            hour,
            minute,
            0,
            0,
            ZoneId.of("Asia/Kolkata")
        );
        assertThat(sessions.at(MarketExchange.NSE, time).phase()).isEqualTo(expected);
    }
}
