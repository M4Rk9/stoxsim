package com.stoxsim.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.stoxsim.calendar.domain.MarketPhase;
import com.stoxsim.calendar.repository.MarketHolidayRepository;
import com.stoxsim.instrument.domain.MarketExchange;

class UnitedStatesMarketSessionTest {

    private final MarketHolidayRepository holidays = mock(
        MarketHolidayRepository.class
    );
    private final IndiaMarketSessionService sessions =
        new IndiaMarketSessionService(
            Clock.fixed(
                Instant.parse("2026-08-03T14:00:00Z"),
                ZoneOffset.UTC
            ),
            holidays
        );

    @Test
    void resolvesRegularSessionInNewYorkTimezone() {
        var snapshot = sessions.at(
            MarketExchange.NASDAQ,
            ZonedDateTime.parse(
                "2026-08-03T10:00:00-04:00[America/New_York]"
            )
        );

        assertThat(snapshot.phase()).isEqualTo(MarketPhase.REGULAR);
        assertThat(snapshot.timezone()).isEqualTo("America/New_York");
        assertThat(snapshot.executable()).isTrue();
    }

    @Test
    void acceptsOrdersButDoesNotExecuteDuringPreMarket() {
        var snapshot = sessions.at(
            MarketExchange.NYSE,
            ZonedDateTime.parse(
                "2026-08-03T08:00:00-04:00[America/New_York]"
            )
        );

        assertThat(snapshot.phase()).isEqualTo(MarketPhase.PRE_MARKET);
        assertThat(snapshot.allowsOrderEntry()).isTrue();
        assertThat(snapshot.executable()).isFalse();
    }
}
