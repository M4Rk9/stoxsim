package com.stoxsim.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.MarketDataService;
import com.stoxsim.watchlist.api.AddWatchlistItemRequest;
import com.stoxsim.watchlist.domain.Watchlist;
import com.stoxsim.watchlist.domain.WatchlistItem;
import com.stoxsim.watchlist.repository.WatchlistItemRepository;
import com.stoxsim.watchlist.repository.WatchlistRepository;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistRepository watchlists;
    @Mock private WatchlistItemRepository items;
    @Mock private AppUserRepository users;
    @Mock private TradableInstrumentRepository instruments;
    @Mock private MarketDataService marketData;
    @Mock private ApplicationEventPublisher events;
    @Mock private Watchlist watchlist;
    @Mock private WatchlistItem item;
    @Mock private TradableInstrument instrument;

    private WatchlistService service;
    private UUID userId;
    private UUID watchlistId;
    private UUID instrumentId;

    @BeforeEach
    void setUp() {
        service = new WatchlistService(
            watchlists,
            items,
            users,
            instruments,
            marketData,
            events
        );
        userId = UUID.randomUUID();
        watchlistId = UUID.randomUUID();
        instrumentId = UUID.randomUUID();
        when(watchlists.findByUserIdAndDefaultWatchlistTrue(userId))
            .thenReturn(Optional.of(watchlist));
        when(watchlist.getId()).thenReturn(watchlistId);
        when(watchlist.getName()).thenReturn("My Watchlist");
    }

    @Test
    void returnsLivePriceAndCalculatedChangeForSavedItem() {
        Instant now = Instant.now();
        when(items.findAllByWatchlistIdOrderByCreatedAtDesc(watchlistId))
            .thenReturn(List.of(item));
        when(item.getInstrument()).thenReturn(instrument);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getCreatedAt()).thenReturn(now.minusSeconds(60));
        stubInstrument();
        when(marketData.latestQuote(instrument)).thenReturn(new Quote(
            new InstrumentKey("UPSTOX", "NSE_EQ|INE002A01018", MarketRegion.INDIA),
            new BigDecimal("2510.00"),
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("2500.00"),
            null,
            now,
            now
        ));
        when(marketData.status(instrument, org.mockito.ArgumentMatchers.any()))
            .thenReturn(MarketDataStatus.LIVE);

        var response = service.getDefault(userId);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().lastPrice()).isEqualByComparingTo("2510.00");
        assertThat(response.items().getFirst().change()).isEqualByComparingTo("10.00");
        assertThat(response.items().getFirst().changePercent()).isEqualByComparingTo("0.4000");
        assertThat(response.items().getFirst().dataStatus()).isEqualTo("LIVE");
    }

    @Test
    void addingAnExistingInstrumentIsIdempotent() {
        when(instruments.findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(
            MarketRegion.INDIA,
            MarketExchange.NSE,
            "RELIANCE"
        )).thenReturn(Optional.of(instrument));
        when(instrument.getId()).thenReturn(instrumentId);
        when(instrument.getInstrumentType()).thenReturn(InstrumentType.EQUITY);
        when(items.findByWatchlistIdAndInstrumentId(watchlistId, instrumentId))
            .thenReturn(Optional.of(item));
        when(items.findAllByWatchlistIdOrderByCreatedAtDesc(watchlistId))
            .thenReturn(List.of());

        service.add(
            userId,
            new AddWatchlistItemRequest(MarketRegion.INDIA, MarketExchange.NSE, "RELIANCE")
        );

        verify(items, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    private void stubInstrument() {
        when(instrument.getId()).thenReturn(instrumentId);
        when(instrument.getProvider()).thenReturn("UPSTOX");
        when(instrument.getInstrumentKey()).thenReturn("NSE_EQ|INE002A01018");
        when(instrument.getMarketRegion()).thenReturn(MarketRegion.INDIA);
        when(instrument.getExchange()).thenReturn(MarketExchange.NSE);
        when(instrument.getTradingSymbol()).thenReturn("RELIANCE");
        when(instrument.getName()).thenReturn("Reliance Industries Limited");
        when(instrument.getInstrumentType()).thenReturn(InstrumentType.EQUITY);
        when(instrument.getCurrency()).thenReturn("INR");
        when(instrument.getTickSize()).thenReturn(new BigDecimal("0.05"));
    }
}
