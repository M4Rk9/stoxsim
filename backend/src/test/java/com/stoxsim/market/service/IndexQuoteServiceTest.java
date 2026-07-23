package com.stoxsim.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;

@ExtendWith(MockitoExtension.class)
class IndexQuoteServiceTest {

    @Mock private TradableInstrumentRepository instruments;
    @Mock private MarketDataService marketData;

    @Test
    void calculatesPointAndPercentageChangeAndKeepsMissingIndicesVisible() {
        TradableInstrument nifty = index("NSE_INDEX|Nifty 50", "Nifty 50");
        when(instruments.findAllByProviderAndInstrumentKeyIn(
            org.mockito.ArgumentMatchers.eq("UPSTOX"),
            anyCollection()
        )).thenReturn(List.of(nifty));

        Instant now = Instant.now();
        when(marketData.latestQuote(nifty)).thenReturn(new Quote(
            new InstrumentKey("UPSTOX", "NSE_INDEX|Nifty 50", MarketRegion.INDIA),
            new BigDecimal("25050.00"),
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("25000.00"),
            null,
            now,
            now
        ));
        when(marketData.status(
            org.mockito.ArgumentMatchers.eq(nifty),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(MarketDataStatus.LIVE);

        var responses = new IndexQuoteService(instruments, marketData).current();

        assertThat(responses).hasSize(6);
        assertThat(responses.getFirst().value()).isEqualByComparingTo("25050.0000");
        assertThat(responses.getFirst().change()).isEqualByComparingTo("50.0000");
        assertThat(responses.getFirst().changePercent()).isEqualByComparingTo("0.2000");
        assertThat(responses.getFirst().dataStatus()).isEqualTo(MarketDataStatus.LIVE);
        assertThat(responses.get(1).dataStatus()).isEqualTo(MarketDataStatus.UNAVAILABLE);
    }

    private TradableInstrument index(String key, String name) {
        return new TradableInstrument(
            new InstrumentSnapshot(
                "UPSTOX",
                key,
                MarketRegion.INDIA,
                MarketExchange.NSE,
                "NSE_INDEX",
                name,
                name,
                null,
                InstrumentType.INDEX,
                "INR",
                1,
                new BigDecimal("0.05"),
                null
            ),
            UUID.randomUUID(),
            Instant.now()
        );
    }
}
