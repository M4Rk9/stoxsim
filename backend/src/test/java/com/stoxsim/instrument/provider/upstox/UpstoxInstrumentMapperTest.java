package com.stoxsim.instrument.provider.upstox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;

class UpstoxInstrumentMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UpstoxInstrumentMapper mapper = new UpstoxInstrumentMapper();

    @Test
    void mapsSupportedNseEquity() {
        var node = objectMapper.readTree("""
            {
              "segment": "NSE_EQ",
              "instrument_key": "NSE_EQ|INE002A01018",
              "trading_symbol": "RELIANCE",
              "name": "Reliance Industries Limited",
              "isin": "INE002A01018",
              "instrument_type": "EQ",
              "lot_size": 1,
              "tick_size": 0.05,
              "security_type": "NORMAL"
            }
            """);

        var snapshot = mapper.map(node).orElseThrow();

        assertThat(snapshot.exchange()).isEqualTo(MarketExchange.NSE);
        assertThat(snapshot.instrumentType()).isEqualTo(InstrumentType.EQUITY);
        assertThat(snapshot.instrumentKey()).isEqualTo("NSE_EQ|INE002A01018");
        assertThat(snapshot.currency()).isEqualTo("INR");
    }

    @Test
    void ignoresDerivativeSegments() {
        var node = objectMapper.readTree("""
            {
              "segment": "NSE_FO",
              "instrument_key": "NSE_FO|12345",
              "trading_symbol": "RELIANCE FUT",
              "name": "Reliance Futures"
            }
            """);

        assertThat(mapper.map(node)).isEmpty();
    }
}
