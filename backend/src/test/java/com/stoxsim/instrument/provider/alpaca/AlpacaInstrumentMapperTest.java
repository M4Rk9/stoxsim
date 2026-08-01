package com.stoxsim.instrument.provider.alpaca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;

import tools.jackson.databind.ObjectMapper;

class AlpacaInstrumentMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AlpacaInstrumentMapper mapper = new AlpacaInstrumentMapper();

    @Test
    void mapsActiveTradableUsAsset() throws Exception {
        var asset = objectMapper.readTree("""
            {
              "id": "asset-id",
              "class": "us_equity",
              "exchange": "NASDAQ",
              "symbol": "AAPL",
              "name": "Apple Inc. Common Stock",
              "status": "active",
              "tradable": true
            }
            """);

        var snapshot = mapper.map(asset).orElseThrow();

        assertThat(snapshot.provider()).isEqualTo("ALPACA");
        assertThat(snapshot.marketRegion()).isEqualTo(
            MarketRegion.UNITED_STATES
        );
        assertThat(snapshot.exchange()).isEqualTo(MarketExchange.NASDAQ);
        assertThat(snapshot.instrumentType()).isEqualTo(InstrumentType.EQUITY);
        assertThat(snapshot.currency()).isEqualTo("USD");
    }
}
