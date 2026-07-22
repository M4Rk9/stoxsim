package com.stoxsim.order.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.stoxsim.market.data.MarketTick;
import com.stoxsim.order.repository.PaperOrderRepository;

@Component
public class OrderTickMatcher {

    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private final PaperOrderRepository orders;
    private final OrderSettlementService settlement;

    public OrderTickMatcher(
        PaperOrderRepository orders,
        OrderSettlementService settlement
    ) {
        this.orders = orders;
        this.settlement = settlement;
    }

    public void onTick(MarketTick tick) {
        LocalDate tradeDate = LocalDate.now(INDIA_ZONE);
        orders.findOpenIdsForTick(
            tick.quote().instrument().provider(),
            tick.quote().instrument().value(),
            tradeDate
        ).forEach(orderId -> settlement.tryFill(orderId, tick.quote()));
    }
}
