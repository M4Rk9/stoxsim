package com.stoxsim.watchlist.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.service.MarketDataService;
import com.stoxsim.watchlist.api.AddWatchlistItemRequest;
import com.stoxsim.watchlist.api.WatchlistItemResponse;
import com.stoxsim.watchlist.api.WatchlistResponse;
import com.stoxsim.watchlist.domain.Watchlist;
import com.stoxsim.watchlist.domain.WatchlistItem;
import com.stoxsim.watchlist.repository.WatchlistItemRepository;
import com.stoxsim.watchlist.repository.WatchlistRepository;

@Service
public class WatchlistService {

    private static final String DEFAULT_NAME = "My Watchlist";

    private final WatchlistRepository watchlists;
    private final WatchlistItemRepository items;
    private final AppUserRepository users;
    private final TradableInstrumentRepository instruments;
    private final MarketDataService marketData;
    private final ApplicationEventPublisher events;

    public WatchlistService(
        WatchlistRepository watchlists,
        WatchlistItemRepository items,
        AppUserRepository users,
        TradableInstrumentRepository instruments,
        MarketDataService marketData,
        ApplicationEventPublisher events
    ) {
        this.watchlists = watchlists;
        this.items = items;
        this.users = users;
        this.instruments = instruments;
        this.marketData = marketData;
        this.events = events;
    }

    @Transactional
    public WatchlistResponse getDefault(UUID userId) {
        Watchlist watchlist = defaultWatchlist(userId);
        return response(watchlist);
    }

    @Transactional
    public WatchlistResponse add(UUID userId, AddWatchlistItemRequest request) {
        Watchlist watchlist = defaultWatchlist(userId);
        TradableInstrument instrument = instruments
            .findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(
                request.marketRegion(),
                request.exchange(),
                request.symbol().trim()
            )
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Instrument not found"
            ));
        if (instrument.getInstrumentType() != InstrumentType.EQUITY
            && instrument.getInstrumentType() != InstrumentType.ETF) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only equities and ETFs can be added to a watchlist"
            );
        }

        if (items.findByWatchlistIdAndInstrumentId(watchlist.getId(), instrument.getId()).isEmpty()) {
            items.save(new WatchlistItem(watchlist, instrument));
            events.publishEvent(new WatchlistSubscriptionAddedEvent(key(instrument)));
        }
        return response(watchlist);
    }

    @Transactional
    public WatchlistResponse remove(UUID userId, UUID itemId) {
        Watchlist watchlist = defaultWatchlist(userId);
        WatchlistItem item = items.findByIdAndWatchlistId(itemId, watchlist.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Watchlist item not found"
            ));
        InstrumentKey key = key(item.getInstrument());
        items.delete(item);
        events.publishEvent(new WatchlistSubscriptionRemovedEvent(key));
        return response(watchlist);
    }

    private Watchlist defaultWatchlist(UUID userId) {
        return watchlists.findByUserIdAndDefaultWatchlistTrue(userId).orElseGet(() -> {
            var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User not found"
            ));
            return watchlists.save(new Watchlist(user, DEFAULT_NAME, true));
        });
    }

    private WatchlistResponse response(Watchlist watchlist) {
        var responses = items.findAllByWatchlistIdOrderByCreatedAtDesc(watchlist.getId())
            .stream()
            .map(this::itemResponse)
            .toList();
        return new WatchlistResponse(watchlist.getId(), watchlist.getName(), responses);
    }

    private WatchlistItemResponse itemResponse(WatchlistItem item) {
        TradableInstrument instrument = item.getInstrument();
        try {
            Quote quote = marketData.latestQuote(instrument);
            BigDecimal change = change(quote);
            return itemResponse(
                item,
                quote.lastPrice(),
                change,
                changePercent(change, quote.previousClose()),
                marketData.status(instrument, quote).name(),
                quote.exchangeTimestamp() == null ? quote.receivedAt() : quote.exchangeTimestamp()
            );
        } catch (RuntimeException exception) {
            return itemResponse(item, null, null, null, "UNAVAILABLE", null);
        }
    }

    private WatchlistItemResponse itemResponse(
        WatchlistItem item,
        BigDecimal lastPrice,
        BigDecimal change,
        BigDecimal changePercent,
        String dataStatus,
        java.time.Instant exchangeTimestamp
    ) {
        TradableInstrument instrument = item.getInstrument();
        return new WatchlistItemResponse(
            item.getId(),
            instrument.getId(),
            instrument.getProvider(),
            instrument.getInstrumentKey(),
            instrument.getMarketRegion(),
            instrument.getExchange(),
            instrument.getTradingSymbol(),
            instrument.getName(),
            instrument.getInstrumentType(),
            instrument.getCurrency(),
            instrument.getTickSize(),
            lastPrice,
            change,
            changePercent,
            dataStatus,
            exchangeTimestamp,
            item.getCreatedAt()
        );
    }

    private BigDecimal change(Quote quote) {
        if (quote.lastPrice() == null || quote.previousClose() == null) {
            return null;
        }
        return quote.lastPrice().subtract(quote.previousClose());
    }

    private BigDecimal changePercent(BigDecimal change, BigDecimal previousClose) {
        if (change == null || previousClose == null || previousClose.signum() == 0) {
            return null;
        }
        return change.multiply(BigDecimal.valueOf(100))
            .divide(previousClose, 4, RoundingMode.HALF_UP);
    }

    private InstrumentKey key(TradableInstrument instrument) {
        return new InstrumentKey(
            instrument.getProvider(),
            instrument.getInstrumentKey(),
            instrument.getMarketRegion()
        );
    }
}
