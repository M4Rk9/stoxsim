package com.stoxsim.order.service;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.PaperOrder;
import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.portfolio.repository.HoldingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Caller holds the account lock before acquiring order/holding locks. */
@Service
public class SandboxOrderCleanup {
    private final PaperOrderRepository orders;
    private final HoldingRepository holdings;
    private final ApplicationEventPublisher events;
    public SandboxOrderCleanup(PaperOrderRepository orders, HoldingRepository holdings, ApplicationEventPublisher events) {
        this.orders=orders; this.holdings=holdings; this.events=events;
    }
    public void cancelOpenForAccount(VirtualAccount account) {
        for (var id : orders.findOpenIdsByAccountId(account.getId())) {
            var order=orders.findByIdForUpdate(id).orElseThrow();
            cancel(order,account);
        }
    }
    public void cancel(PaperOrder order,VirtualAccount account) {
        if (!order.isOpen()) return;
        if (order.getSide()==OrderSide.BUY) account.releaseReservedCash(order.getReservedCash());
        else holdings.findForUpdate(account.getId(),order.getInstrument().getId()).orElseThrow().release(order.getQuantity());
        order.markCancelled();
        var instrument=order.getInstrument();
        events.publishEvent(new OrderClosedEvent(new InstrumentKey(instrument.getProvider(),instrument.getInstrumentKey(),instrument.getMarketRegion())));
    }
}
