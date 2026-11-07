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
    @Test
    void rejectsNonStockNseCashSeriesIncludingScreenshotBond() {
        for (String series : new String[]{"N0", "GS", "SG", "GB", "TB", "IV", "RR", ""}) {
            var node = objectMapper.readTree("""
                {"segment":"NSE_EQ","instrument_key":"NSE_EQ|INE002A08708",
                 "instrument_type":"%s","trading_symbol":"747RIL31","name":"RIL 7.47%% 2031 SR Q"}
                """.formatted(series));
            assertThat(mapper.map(node)).as(series).isEmpty();
        }
    }

    @Test
    void recognisesAnEtfInEqSeriesWithNormalSecurityType() {
        var node = objectMapper.readTree("""
            {"segment":"NSE_EQ","instrument_key":"NSE_EQ|INF204KB14I2",
             "instrument_type":"EQ","trading_symbol":"NIFTYBEES","name":"NIP IND ETF NIFTY BEES",
             "isin":"INF204KB14I2","security_type":"NORMAL"}
            """);
        assertThat(mapper.map(node).orElseThrow().instrumentType()).isEqualTo(InstrumentType.ETF);
    }

    @Test
    void keepsStockSeriesAndBenchmarkIndices() {
        for (String series : new String[]{"EQ", "BE", "BZ", "SM", "ST", "SZ"}) {
            var node = objectMapper.readTree("""
                {"segment":"NSE_EQ","instrument_key":"NSE_EQ|INE002A01018",
                 "instrument_type":"%s","trading_symbol":"STOCK","name":"Example stock"}
                """.formatted(series));
            assertThat(mapper.map(node).orElseThrow().instrumentType()).isEqualTo(InstrumentType.EQUITY);
        }
        assertThat(mapper.map(objectMapper.readTree("""
            {"segment":"NSE_INDEX","instrument_key":"NSE_INDEX|Nifty 50","name":"Nifty 50"}
            """)).orElseThrow().instrumentType()).isEqualTo(InstrumentType.INDEX);
    }

    @Test
    void mapsBseStocksInRupeesAndRejectsDebtInTheSameSegment() {
        var equity = objectMapper.readTree("""
            {"segment":"BSE_EQ","instrument_key":"BSE_EQ|INE376L01013",
             "instrument_type":"X","trading_symbol":"RAJKSYN","name":"Rajkamal",
             "isin":"INE376L01013","lot_size":1,"tick_size":5}
            """);
        var debt = objectMapper.readTree("""
            {"segment":"BSE_EQ","instrument_key":"BSE_EQ|INE053F08270",
             "instrument_type":"F","trading_symbol":"775IRFC33","name":"Bond",
             "isin":"INE053F08270","lot_size":1,"tick_size":1}
            """);
        var snapshot = mapper.map(equity).orElseThrow();
        assertThat(snapshot.exchange()).isEqualTo(MarketExchange.BSE);
        assertThat(snapshot.tickSize()).isEqualByComparingTo("0.05");
        assertThat(mapper.map(debt)).isEmpty();
    }
}
