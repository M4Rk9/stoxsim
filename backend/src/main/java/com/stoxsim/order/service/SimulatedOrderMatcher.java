package com.stoxsim.order.service;

import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.market.service.MarketDataService;
import com.stoxsim.calendar.service.IndiaMarketSessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

@Component
public class SimulatedOrderMatcher {
    private final PaperOrderRepository orders;
    private final MarketDataService prices;
    private final OrderSettlementService settlement;
    private final IndiaMarketSessionService sessions;
    public SimulatedOrderMatcher(PaperOrderRepository orders, MarketDataService prices, OrderSettlementService settlement, IndiaMarketSessionService sessions) {
        this.orders=orders; this.prices=prices; this.settlement=settlement; this.sessions=sessions;
    }
    @Scheduled(initialDelay=15000, fixedDelay=15000)
    public void match() {
        for (var order : orders.findOpenWithInstruments()) {
            try {
                if (sessions.current(order.getInstrument().getExchange()).executable())
                    settlement.tryFill(order.getId(), prices.latestQuote(order.getInstrument()));
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(SimulatedOrderMatcher.class).warn("Could not match order {}", order.getId(), e);
            }
        }
    }
}
