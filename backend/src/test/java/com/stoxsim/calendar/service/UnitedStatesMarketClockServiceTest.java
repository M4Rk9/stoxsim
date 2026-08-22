package com.stoxsim.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.market.provider.alpaca.AlpacaRestClient;
import com.stoxsim.market.provider.upstox.MarketDataUnavailableException;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class UnitedStatesMarketClockServiceTest {

    @Mock private AlpacaRestClient alpaca;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsTheAuthoritativeAlpacaClock() throws Exception {
        when(alpaca.getClock()).thenReturn(objectMapper.readTree("""
            {
              "timestamp": "2026-08-24T10:00:00-04:00",
              "is_open": true,
              "next_open": "2026-08-25T09:30:00-04:00",
              "next_close": "2026-08-24T16:00:00-04:00"
            }
            """));

        var clock = new UnitedStatesMarketClockService(alpaca).current();

        assertThat(clock.open()).isTrue();
        assertThat(clock.timestamp()).isEqualTo(Instant.parse("2026-08-24T14:00:00Z"));
        assertThat(clock.nextOpen()).isEqualTo(Instant.parse("2026-08-25T13:30:00Z"));
        assertThat(clock.nextClose()).isEqualTo(Instant.parse("2026-08-24T20:00:00Z"));
    }

    @Test
    void rejectsAnIncompleteProviderResponse() throws Exception {
        when(alpaca.getClock()).thenReturn(objectMapper.readTree("{}"));

        assertThatThrownBy(() -> new UnitedStatesMarketClockService(alpaca).current())
            .isInstanceOf(MarketDataUnavailableException.class)
            .hasMessage("Alpaca returned an incomplete market clock");
    }
}
