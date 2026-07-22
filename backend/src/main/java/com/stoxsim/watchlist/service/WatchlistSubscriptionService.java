package com.stoxsim.watchlist.service;

import java.util.Set;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.SubscriptionMode;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.watchlist.repository.WatchlistItemRepository;

@Component
public class WatchlistSubscriptionService {

    private final MarketDataProviderRegistry providers;
    private final WatchlistItemRepository items;

    public WatchlistSubscriptionService(
        MarketDataProviderRegistry providers,
        WatchlistItemRepository items
    ) {
        this.providers = providers;
        this.items = items;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void restoreSubscriptions() {
        items.findAllWithInstrument().forEach(item -> subscribe(key(item.getInstrument())));
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true
    )
    public void onAdded(WatchlistSubscriptionAddedEvent event) {
        subscribe(event.instrument());
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true
    )
    public void onRemoved(WatchlistSubscriptionRemovedEvent event) {
        providers.forRegion(event.instrument().marketRegion())
            .unsubscribe(Set.of(event.instrument()));
    }

    private void subscribe(InstrumentKey instrument) {
        providers.forRegion(instrument.marketRegion()).subscribe(
            Set.of(instrument),
            SubscriptionMode.FULL,
            null
        );
    }

    private InstrumentKey key(TradableInstrument instrument) {
        return new InstrumentKey(
            instrument.getProvider(),
            instrument.getInstrumentKey(),
            instrument.getMarketRegion()
        );
    }
}
