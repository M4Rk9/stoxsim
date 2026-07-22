package com.stoxsim.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.MarketDataService;
import com.stoxsim.order.api.ModifyOrderRequest;
import com.stoxsim.order.api.OrderResponse;
import com.stoxsim.order.api.PlaceOrderRequest;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.OrderStatus;
import com.stoxsim.order.domain.OrderType;
import com.stoxsim.order.domain.PaperOrder;
import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.portfolio.domain.Holding;
import com.stoxsim.portfolio.repository.HoldingRepository;

@Service
public class OrderApplicationService {

    private final VirtualAccountRepository accounts;
    private final TradableInstrumentRepository instruments;
    private final HoldingRepository holdings;
    private final PaperOrderRepository orders;
    private final MarketDataService marketData;
    private final IndiaMarketSessionService sessions;
    private final ExecutionPriceCalculator prices;
    private final OrderSettlementService settlement;
    private final ApplicationEventPublisher events;

    public OrderApplicationService(
        VirtualAccountRepository accounts,
        TradableInstrumentRepository instruments,
        HoldingRepository holdings,
        PaperOrderRepository orders,
        MarketDataService marketData,
        IndiaMarketSessionService sessions,
        ExecutionPriceCalculator prices,
        OrderSettlementService settlement,
        ApplicationEventPublisher events
    ) {
        this.accounts = accounts;
        this.instruments = instruments;
        this.holdings = holdings;
        this.orders = orders;
        this.marketData = marketData;
        this.sessions = sessions;
        this.prices = prices;
        this.settlement = settlement;
        this.events = events;
    }

    @Transactional
    public OrderResponse place(UUID userId, String idempotencyKey, PlaceOrderRequest request) {
        validateIdempotencyKey(idempotencyKey);
        validateRequest(request);

        VirtualAccount account = accounts.findForUpdate(userId, request.marketRegion())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        var existing = orders.findByAccountIdAndIdempotencyKey(
            account.getId(),
            idempotencyKey.trim()
        );
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }

        TradableInstrument instrument = findInstrument(request);
        validateTickSize(instrument, request.limitPrice());
        var session = sessions.current(instrument.getExchange());
        ensureOrderChangesAllowed(session.allowsOrderEntry());

        Quote quote = marketData.latestQuote(instrument);
        if (marketData.isStale(quote)) {
            throw new TradingValidationException("Stale quote cannot be used for an order");
        }

        BigDecimal reservedCash = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (request.side() == OrderSide.BUY) {
            BigDecimal reservationPrice = prices.reservationPrice(
                request.side(),
                request.orderType(),
                request.limitPrice(),
                quote
            );
            reservedCash = value(reservationPrice, request.quantity());
            account.reserveCash(reservedCash);
        } else {
            Holding holding = holdings
                .findForUpdate(account.getId(), instrument.getId())
                .orElseThrow(() -> new TradingValidationException(
                    "No holding is available for this sell order"
                ));
            holding.reserve(request.quantity());
        }

        PaperOrder order = orders.save(new PaperOrder(
            account,
            instrument,
            idempotencyKey.trim(),
            request.side(),
            request.orderType(),
            request.quantity(),
            request.limitPrice(),
            reservedCash,
            session.orderDate()
        ));

        events.publishEvent(new OrderOpenedEvent(key(order)));
        if (session.executable()) {
            settlement.settleOpenOrder(order, account, quote);
        }
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(UUID userId, MarketRegion marketRegion) {
        return orders
            .findAllByAccountUserIdAndAccountMarketRegionOrderByCreatedAtDesc(userId, marketRegion)
            .stream()
            .map(OrderResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID userId, UUID orderId) {
        return orders.findByIdAndAccountUserId(orderId, userId)
            .map(OrderResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @Transactional
    public OrderResponse modify(UUID userId, UUID orderId, ModifyOrderRequest request) {
        PaperOrder snapshot = ownedOrder(userId, orderId);
        VirtualAccount account = accounts.findByIdForUpdate(snapshot.getAccount().getId())
            .orElseThrow();
        PaperOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        ensureOwner(order, userId);

        var session = sessions.current(order.getInstrument().getExchange());
        ensureOrderChangesAllowed(session.allowsOrderEntry());
        validateLimitTerms(order.getOrderType(), request.quantity(), request.limitPrice());
        validateTickSize(order.getInstrument(), request.limitPrice());

        if (order.getSide() == OrderSide.BUY) {
            BigDecimal newReservation = value(request.limitPrice(), request.quantity());
            BigDecimal difference = newReservation.subtract(order.getReservedCash());
            if (difference.signum() > 0) {
                account.reserveCash(difference);
            } else if (difference.signum() < 0) {
                account.releaseReservedCash(difference.abs());
            }
            order.changeTerms(request.quantity(), request.limitPrice(), newReservation);
        } else {
            Holding holding = holdings
                .findForUpdate(account.getId(), order.getInstrument().getId())
                .orElseThrow();
            long difference = request.quantity() - order.getQuantity();
            if (difference > 0) {
                holding.reserve(difference);
            } else if (difference < 0) {
                holding.release(-difference);
            }
            order.changeTerms(
                request.quantity(),
                request.limitPrice(),
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            );
        }

        if (session.executable()) {
            Quote quote = marketData.latestQuote(order.getInstrument());
            if (marketData.isStale(quote)) {
                throw new TradingValidationException("Stale quote cannot be used for an order");
            }
            settlement.settleOpenOrder(order, account, quote);
        }
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UUID userId, UUID orderId) {
        PaperOrder snapshot = ownedOrder(userId, orderId);
        VirtualAccount account = accounts.findByIdForUpdate(snapshot.getAccount().getId())
            .orElseThrow();
        PaperOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        ensureOwner(order, userId);
        ensureOrderChangesAllowed(
            sessions.current(order.getInstrument().getExchange()).allowsOrderEntry()
        );

        releaseReservation(order, account);
        order.markCancelled();
        events.publishEvent(new OrderClosedEvent(key(order)));
        return OrderResponse.from(order);
    }

    @Transactional
    public void expire(UUID orderId) {
        PaperOrder snapshot = orders.findById(orderId).orElse(null);
        if (snapshot == null || snapshot.getStatus() != OrderStatus.OPEN) {
            return;
        }
        VirtualAccount account = accounts.findByIdForUpdate(snapshot.getAccount().getId())
            .orElseThrow();
        PaperOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (!order.isOpen()) {
            return;
        }
        releaseReservation(order, account);
        order.markExpired();
        events.publishEvent(new OrderClosedEvent(key(order)));
    }

    private void releaseReservation(PaperOrder order, VirtualAccount account) {
        if (order.getSide() == OrderSide.BUY) {
            account.releaseReservedCash(order.getReservedCash());
            return;
        }
        holdings.findForUpdate(account.getId(), order.getInstrument().getId())
            .orElseThrow()
            .release(order.getQuantity());
    }

    private PaperOrder ownedOrder(UUID userId, UUID orderId) {
        return orders.findByIdAndAccountUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void ensureOwner(PaperOrder order, UUID userId) {
        if (!order.getAccount().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    private TradableInstrument findInstrument(PlaceOrderRequest request) {
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
        if (
            instrument.getMarketRegion() != MarketRegion.INDIA
            || instrument.getExchange() != MarketExchange.NSE
            || (
                instrument.getInstrumentType() != InstrumentType.EQUITY
                && instrument.getInstrumentType() != InstrumentType.ETF
            )
        ) {
            throw new TradingValidationException(
                "The MVP supports NSE cash equities and ETFs only"
            );
        }
        return instrument;
    }

    private void validateRequest(PlaceOrderRequest request) {
        if (request.marketRegion() != MarketRegion.INDIA || request.exchange() != MarketExchange.NSE) {
            throw new TradingValidationException("The MVP supports NSE delivery orders only");
        }
        validateLimitTerms(request.orderType(), request.quantity(), request.limitPrice());
    }

    private void validateLimitTerms(OrderType type, long quantity, BigDecimal limitPrice) {
        if (quantity <= 0) {
            throw new TradingValidationException("Quantity must be positive");
        }
        if (type == OrderType.MARKET && limitPrice != null) {
            throw new TradingValidationException("Market orders cannot include a limit price");
        }
        if (type == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new TradingValidationException("Limit orders require a positive limit price");
        }
    }

    private void validateTickSize(TradableInstrument instrument, BigDecimal price) {
        if (
            price != null
            && instrument.getTickSize().signum() > 0
            && price.remainder(instrument.getTickSize()).compareTo(BigDecimal.ZERO) != 0
        ) {
            throw new TradingValidationException(
                "Limit price must be a multiple of tick size " + instrument.getTickSize()
            );
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new TradingValidationException(
                "Idempotency-Key must contain between 1 and 100 characters"
            );
        }
    }

    private void ensureOrderChangesAllowed(boolean allowed) {
        if (!allowed) {
            throw new TradingValidationException(
                "Orders are frozen during pre-open matching and buffer phases"
            );
        }
    }

    private BigDecimal value(BigDecimal price, long quantity) {
        return price.multiply(BigDecimal.valueOf(quantity))
            .setScale(4, RoundingMode.HALF_UP);
    }

    private InstrumentKey key(PaperOrder order) {
        return new InstrumentKey(
            order.getInstrument().getProvider(),
            order.getInstrument().getInstrumentKey(),
            order.getInstrument().getMarketRegion()
        );
    }
}
