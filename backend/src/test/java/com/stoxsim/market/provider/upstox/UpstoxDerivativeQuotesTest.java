package com.stoxsim.market.provider.upstox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UpstoxDerivativeQuotesTest {
    @Test
    void verifiesExactContractAndExchangeTradeTimeRatherThanReceiptTimeAlone() {
        var now = Instant.parse("2026-11-06T06:00:00Z");
        var parser = new ObjectMapper();
        String fixture = """
            {"NSE_FO:OPTION":{"instrument_token":"NSE_FO|17","last_price":90.5,"volume":230,
             "last_trade_time":"%s","depth":{"buy":[{"price":90}],"sell":[{"price":91}]}}}
            """;
        var verifier = new UpstoxDerivativeQuotes(null, null, parser, Clock.fixed(now, ZoneOffset.UTC));
        var fresh = parser.readTree(fixture.formatted(now.minusSeconds(10).toEpochMilli()));
        assertThat(UpstoxDerivativeQuotes.parse(fresh, List.of("NSE_FO|999"), now)).isEmpty();
        var quote = UpstoxDerivativeQuotes.parse(fresh, List.of("NSE_FO|17"), now).get("NSE_FO|17");
        assertThat(quote.ask()).isEqualByComparingTo("91");
        assertThat(verifier.isFresh(quote)).isTrue();
        var stale = parser.readTree(fixture.formatted(now.minusSeconds(61).toEpochMilli()));
        assertThat(verifier.isFresh(UpstoxDerivativeQuotes.parse(stale, List.of("NSE_FO|17"), now).get("NSE_FO|17"))).isFalse();
    }
}
