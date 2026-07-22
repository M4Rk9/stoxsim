package com.stoxsim.order.service;

import java.util.Set;
import java.util.function.Consumer;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketTick;
import com.stoxsim.market.data.SubscriptionMode;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.order.domain.OrderStatus;
import com.stoxsim.order.repository.PaperOrderRepository;

@Component
public class OrderSubscriptionService {

    private final MarketDataProviderRegistry providers;
    private final PaperOrderRepository orders;
    private final Consumer<MarketTick> tickListener;

    public OrderSubscriptionService(
        MarketDataProviderRegistry providers,
        PaperOrderRepository orders,
        OrderTickMatcher matcher
    ) {
        this.providers = providers;
        this.orders = orders;
        this.tickListener = matcher::onTick;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void restoreOpenOrderSubscriptions() {
        orders.findAllByStatus(OrderStatus.OPEN).forEach(order -> subscribe(new InstrumentKey(
            order.getInstrument().getProvider(),
            order.getInstrument().getInstrumentKey(),
            order.getInstrument().getMarketRegion()
        )));
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true
    )
    public void onOpened(OrderOpenedEvent event) {
        subscribe(event.instrument());
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true
    )
    public void onClosed(OrderClosedEvent event) {
        providers.forRegion(event.instrument().marketRegion())
            .unsubscribe(Set.of(event.instrument()));
    }

    private void subscribe(InstrumentKey instrument) {
        providers.forRegion(instrument.marketRegion()).subscribe(
            Set.of(instrument),
            SubscriptionMode.FULL,
            tickListener
        );
    }
}
