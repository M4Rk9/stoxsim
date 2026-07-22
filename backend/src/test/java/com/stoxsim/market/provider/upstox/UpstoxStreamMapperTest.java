package com.stoxsim.market.provider.upstox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.upstox.feeder.MarketUpdateV3;

class UpstoxStreamMapperTest {

    private final UpstoxStreamMapper mapper = new UpstoxStreamMapper();

    @Test
    void mapsLtpcFeedIntoProviderIndependentQuote() {
        var ltpc = new MarketUpdateV3.LTPC();
        ltpc.setLtp(2510.55);
        ltpc.setCp(2490.10);
        ltpc.setLtt(1784691900000L);

        var feed = new MarketUpdateV3.Feed();
        feed.setLtpc(ltpc);

        var update = new MarketUpdateV3();
        update.setFeeds(Map.of("NSE_EQ|INE002A01018", feed));

        var quote = mapper.map(update).getFirst();

        assertThat(quote.instrument().value()).isEqualTo("NSE_EQ|INE002A01018");
        assertThat(quote.lastPrice()).isEqualByComparingTo("2510.55");
        assertThat(quote.previousClose()).isEqualByComparingTo("2490.1");
        assertThat(quote.exchangeTimestamp()).isNotNull();
    }
}
