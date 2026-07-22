package com.stoxsim.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.account.domain.AccountLedgerEntry;
import com.stoxsim.account.domain.AccountLedgerEntry.Direction;
import com.stoxsim.account.domain.AccountLedgerEntry.EntryType;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.AccountLedgerRepository;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.PaperOrder;
import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.portfolio.domain.Holding;
import com.stoxsim.portfolio.repository.HoldingRepository;
import com.stoxsim.trade.domain.Trade;
import com.stoxsim.trade.repository.TradeRepository;

@Service
public class OrderSettlementService {

    private final PaperOrderRepository orders;
    private final VirtualAccountRepository accounts;
    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final AccountLedgerRepository ledger;
    private final ExecutionPriceCalculator prices;
    private final IndiaMarketSessionService sessions;
    private final ApplicationEventPublisher events;

    public OrderSettlementService(
        PaperOrderRepository orders,
        VirtualAccountRepository accounts,
        HoldingRepository holdings,
        TradeRepository trades,
        AccountLedgerRepository ledger,
        ExecutionPriceCalculator prices,
        IndiaMarketSessionService sessions,
        ApplicationEventPublisher events
    ) {
        this.orders = orders;
        this.accounts = accounts;
        this.holdings = holdings;
        this.trades = trades;
        this.ledger = ledger;
        this.prices = prices;
        this.sessions = sessions;
        this.events = events;
    }

    @Transactional
    public boolean tryFill(UUID orderId, Quote quote) {
        PaperOrder snapshot = orders.findById(orderId).orElse(null);
        if (snapshot == null) {
            return false;
        }
        VirtualAccount account = accounts.findByIdForUpdate(snapshot.getAccount().getId())
            .orElseThrow();
        PaperOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        return settleOpenOrder(order, account, quote);
    }

    public boolean settleOpenOrder(PaperOrder order, VirtualAccount account, Quote quote) {
        if (!order.isOpen()) {
            return false;
        }
        var session = sessions.current(order.getInstrument().getExchange());
        if (!session.executable() || order.getSubmittedForDate().isAfter(session.orderDate())) {
            return false;
        }

        var fill = prices.fillPrice(order, quote);
        if (fill.isEmpty()) {
            return false;
        }

        BigDecimal price = fill.get();
        BigDecimal grossValue = price
            .multiply(BigDecimal.valueOf(order.getQuantity()))
            .setScale(4, RoundingMode.HALF_UP);
        Instant executedAt = Instant.now();

        if (order.getSide() == OrderSide.BUY) {
            try {
                account.settleReservedCash(order.getReservedCash(), grossValue);
            } catch (TradingValidationException exception) {
                account.releaseReservedCash(order.getReservedCash());
                order.markRejected(exception.getMessage());
                events.publishEvent(new OrderClosedEvent(key(order)));
                return false;
            }
            Holding holding = holdings
                .findForUpdate(account.getId(), order.getInstrument().getId())
                .orElse(null);
            if (holding == null) {
                holding = new Holding(
                    account,
                    order.getInstrument(),
                    order.getQuantity(),
                    price
                );
            } else {
                holding.buy(order.getQuantity(), price);
            }
            holdings.save(holding);
        } else {
            Holding holding = holdings
                .findForUpdate(account.getId(), order.getInstrument().getId())
                .orElseThrow(() -> new IllegalStateException("Reserved holding no longer exists"));
            BigDecimal realized = holding.sellReserved(order.getQuantity(), price);
            account.creditCash(grossValue);
            account.addRealizedProfitLoss(realized);
        }

        order.markExecuted(price, grossValue, executedAt);
        Trade trade = trades.save(new Trade(order, price, grossValue, executedAt));
        ledger.save(new AccountLedgerEntry(
            account,
            order,
            trade,
            order.getSide() == OrderSide.BUY ? EntryType.TRADE_BUY : EntryType.TRADE_SELL,
            order.getSide() == OrderSide.BUY ? Direction.DEBIT : Direction.CREDIT,
            grossValue,
            order.getSide() + " " + order.getQuantity() + " "
                + order.getInstrument().getTradingSymbol(),
            executedAt
        ));
        events.publishEvent(new OrderClosedEvent(key(order)));
        return true;
    }

    private InstrumentKey key(PaperOrder order) {
        return new InstrumentKey(
            order.getInstrument().getProvider(),
            order.getInstrument().getInstrumentKey(),
            order.getInstrument().getMarketRegion()
        );
    }
}
