package com.stoxsim.market.provider.alpaca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.domain.MarketRegion;

import tools.jackson.databind.ObjectMapper;

class AlpacaQuoteMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AlpacaQuoteMapper mapper = new AlpacaQuoteMapper();

    @Test
    void mapsSnapshotIntoProviderNeutralQuote() throws Exception {
        var snapshot = objectMapper.readTree("""
            {
              "latestTrade": {"p": 210.45, "t": "2026-08-01T15:59:59Z"},
              "latestQuote": {"bp": 210.44, "ap": 210.46},
              "dailyBar": {
                "o": 205.10,
                "h": 211.00,
                "l": 204.80,
                "c": 210.45,
                "v": 1234567,
                "t": "2026-08-01T13:30:00Z"
              },
              "prevDailyBar": {"c": 204.25}
            }
            """);

        var quote = mapper.mapQuote(
            new InstrumentKey(
                "ALPACA",
                "AAPL",
                MarketRegion.UNITED_STATES
            ),
            snapshot,
            Instant.parse("2026-08-01T16:00:01Z")
        );

        assertThat(quote.lastPrice()).isEqualByComparingTo("210.45");
        assertThat(quote.bid()).isEqualByComparingTo("210.44");
        assertThat(quote.ask()).isEqualByComparingTo("210.46");
        assertThat(quote.previousClose()).isEqualByComparingTo("204.25");
        assertThat(quote.volume()).isEqualTo(1234567L);
    }
}
