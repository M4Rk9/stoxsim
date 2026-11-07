package com.stoxsim.derivative;

import static com.stoxsim.calendar.service.IndiaMarketSessionService.INDIA_ZONE;
import static com.stoxsim.derivative.DerivativeBook.STARTING_CASH;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.derivative.DerivativeBook.Action;
import com.stoxsim.derivative.DerivativeBook.Direction;
import com.stoxsim.derivative.DerivativeBook.Movement;
import com.stoxsim.derivative.DerivativeBook.Position;
import com.stoxsim.derivative.DerivativeCatalog.Contract;
import com.stoxsim.derivative.DerivativeCatalog.Kind;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.provider.upstox.UpstoxDerivativeQuotes;
import com.stoxsim.order.service.TradingValidationException;

@Service
public class DerivativeTradingService {
    public record ContractQuote(Contract contract, BigDecimal last, BigDecimal bid, BigDecimal ask,
        Long volume, Instant lastTradeAt, boolean fresh) {}
    public record OpenPosition(ContractQuote market, int lots, Direction direction, BigDecimal averageEntry,
        BigDecimal reservedMargin, BigDecimal unrealizedPnl, boolean awaitingSettlement) {}
    public record Fill(UUID id, UUID contractId, String symbol, Action action, int lots, BigDecimal price,
        BigDecimal cashChange, BigDecimal realizedPnl, Instant filledAt) {}
    public record Portfolio(BigDecimal cash, BigDecimal reservedMargin, BigDecimal realizedPnl,
        BigDecimal totalEquity, List<OpenPosition> positions, List<Fill> fills) {}
    private record Wallet(BigDecimal cash, BigDecimal realizedPnl) {}
    private record Holding(Contract contract, Position position) {}

    private final JdbcTemplate db;
    private final DerivativeCatalog catalog;
    private final UpstoxDerivativeQuotes quotes;
    private final IndiaMarketSessionService sessions;
    private final Clock clock;

    public DerivativeTradingService(JdbcTemplate db, DerivativeCatalog catalog, UpstoxDerivativeQuotes quotes,
        IndiaMarketSessionService sessions, Clock clock) {
        this.db = db;
        this.catalog = catalog;
        this.quotes = quotes;
        this.sessions = sessions;
        this.clock = clock;
    }

    public List<ContractQuote> chain(String underlying, LocalDate expiry, int offset) {
        if (expiry.isBefore(today())) throw new TradingValidationException("Choose a current contract expiry.");
        List<Contract> contracts = catalog.chain(underlying, expiry, offset);
        var snapshots = quotes.snapshots(contracts.stream().map(Contract::providerKey).toList());
        return contracts.stream().map(c -> view(c, snapshots)).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Portfolio portfolio(UUID user) {
        Wallet wallet = db.query("SELECT cash, realized_pnl FROM derivative_wallet WHERE user_id=?", this::wallet, user)
            .stream().findFirst().orElse(new Wallet(STARTING_CASH, BigDecimal.ZERO));
        List<Holding> holdings = db.query("""
            SELECT c.*, p.lots, p.direction, p.average_entry, p.reserved_margin
            FROM derivative_position p JOIN derivative_contract c ON c.id=p.contract_id
            WHERE p.user_id=? ORDER BY c.expiry, c.symbol
            """, (row, index) -> new Holding(catalog.contract(row, index), position(row, index)), user);
        var snapshots = quotes.snapshots(holdings.stream().filter(h -> !h.contract().expiry().isBefore(today()))
            .map(h -> h.contract().providerKey()).toList());
        var positions = holdings.stream().map(h -> {
            ContractQuote market = view(h.contract(), snapshots);
            Position p = h.position();
            boolean expired = h.contract().expiry().isBefore(today());
            return new OpenPosition(market, p.lots(), p.direction(), p.averageEntry(), p.reservedMargin(),
                !expired && market.fresh() ? DerivativeBook.pnl(h.contract(), p, p.lots(), market.last()) : null, expired);
        }).toList();
        BigDecimal margin = BigDecimal.ZERO;
        BigDecimal equity = wallet.cash();
        boolean complete = true;
        for (OpenPosition p : positions) {
            margin = margin.add(p.reservedMargin());
            if (p.unrealizedPnl() == null) complete = false;
            else if (p.market().contract().kind() == Kind.FUT) equity = equity.add(p.reservedMargin()).add(p.unrealizedPnl());
            else equity = equity.add(DerivativeBook.value(p.market().contract(), p.lots(), p.market().last()));
        }
        var fills = db.query("""
            SELECT f.*, c.symbol FROM derivative_fill f JOIN derivative_contract c ON c.id=f.contract_id
            WHERE f.user_id=? ORDER BY f.filled_at DESC, f.id LIMIT 25
            """, this::fill, user);
        return new Portfolio(wallet.cash(), margin, wallet.realizedPnl(), complete ? equity : null, positions, fills);
    }

    @Transactional
    public Fill place(UUID user, UUID contractId, UUID requestKey, Action action, int lots) {
        if (!catalog.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (action == Action.EXPIRY || lots < 1 || lots > 100) throw new TradingValidationException("Invalid virtual order.");
        db.update("INSERT INTO derivative_wallet(user_id, cash) VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING", user, STARTING_CASH);
        Wallet wallet = lockWallet(user);
        var previous = db.query("""
            SELECT f.*, c.symbol FROM derivative_fill f JOIN derivative_contract c ON c.id=f.contract_id
            WHERE f.user_id=? AND f.request_key=?
            """, this::fill, user, requestKey);
        if (!previous.isEmpty()) {
            Fill fill = previous.getFirst();
            if (!fill.contractId().equals(contractId) || fill.action() != action || fill.lots() != lots) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request key already belongs to a different virtual order.");
            }
            return fill;
        }

        Contract contract = catalog.active(contractId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract is unavailable."));
        requireSession(contract);
        if (action != Action.CLOSE && !sessions.isTradingDay(MarketExchange.NSE, contract.expiry())) {
            throw new TradingValidationException("The exchange session for this contract's expiry is unverified or closed.");
        }
        Position existing = findPosition(user, contractId);
        if (existing == null && action != Action.CLOSE) {
            Integer count = db.queryForObject("SELECT count(*) FROM derivative_position WHERE user_id=?", Integer.class, user);
            if (count != null && count >= 50) throw new TradingValidationException("Close an existing position before adding another contract.");
        }
        Quote quote = quotes.snapshots(List.of(contract.providerKey())).get(contract.providerKey());
        requireSession(contract);
        if (!quotes.isFresh(quote)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A fresh exchange trade price is required.");
        boolean buy = action == Action.OPEN_LONG || (action == Action.CLOSE && existing != null && existing.direction() == Direction.SHORT);
        BigDecimal price = buy ? quote.ask() : quote.bid();
        if (price == null) price = quote.lastPrice();
        price = price.divide(contract.tickSize(), 0, RoundingMode.HALF_UP).multiply(contract.tickSize());
        return record(user, contract, requestKey, action, lots, price,
            DerivativeBook.trade(contract, existing, action, lots, price, wallet.cash()));
    }

    void settle(UUID user, Contract contract, BigDecimal underlyingClose) {
        Wallet wallet = lockWallet(user);
        Position position = findPosition(user, contract.id());
        if (position == null || !contract.expiry().isBefore(today())) return;
        BigDecimal price = DerivativeBook.expiryPrice(contract, underlyingClose);
        record(user, contract, UUID.randomUUID(), Action.EXPIRY, position.lots(), price,
            DerivativeBook.trade(contract, position, Action.EXPIRY, position.lots(), price, wallet.cash()));
    }

    private Fill record(UUID user, Contract contract, UUID requestKey, Action action, int lots, BigDecimal price, Movement movement) {
        Position p = movement.position();
        if (p == null) db.update("DELETE FROM derivative_position WHERE user_id=? AND contract_id=?", user, contract.id());
        else db.update("""
            INSERT INTO derivative_position(user_id, contract_id, lots, direction, average_entry, reserved_margin)
            VALUES (?,?,?,?,?,?) ON CONFLICT (user_id, contract_id) DO UPDATE SET
            lots=excluded.lots, direction=excluded.direction, average_entry=excluded.average_entry, reserved_margin=excluded.reserved_margin
            """, user, contract.id(), p.lots(), p.direction().name(), p.averageEntry(), p.reservedMargin());
        db.update("UPDATE derivative_wallet SET cash=cash+?, realized_pnl=realized_pnl+? WHERE user_id=?",
            movement.cashChange(), movement.realizedPnl(), user);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        db.update("""
            INSERT INTO derivative_fill(id,user_id,contract_id,request_key,action,lots,price,cash_change,realized_pnl,filled_at)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """, id, user, contract.id(), requestKey, action.name(), lots, price, movement.cashChange(), movement.realizedPnl(),
            java.sql.Timestamp.from(now));
        return new Fill(id, contract.id(), contract.symbol(), action, lots, price, movement.cashChange(), movement.realizedPnl(), now);
    }

    private ContractQuote view(Contract c, Map<String, Quote> snapshots) {
        Quote q = snapshots.get(c.providerKey());
        return q == null ? new ContractQuote(c, null, null, null, null, null, false)
            : new ContractQuote(c, q.lastPrice(), q.bid(), q.ask(), q.volume(), q.exchangeTimestamp(), quotes.isFresh(q));
    }

    private void requireSession(Contract contract) {
        var session = sessions.current(MarketExchange.NSE);
        // Start with regular index sessions. Keep nonstandard and unverified sessions closed for F&O.
        var local = clock.instant().atZone(INDIA_ZONE);
        boolean standardDay = local.getDayOfWeek().getValue() <= 5;
        boolean regularTime = !local.toLocalTime().isBefore(java.time.LocalTime.of(9, 15))
            && local.toLocalTime().isBefore(java.time.LocalTime.of(15, 30));
        if (!standardDay || !regularTime || !session.executable() || contract.expiry().isBefore(local.toLocalDate())) {
            throw new TradingValidationException("The regular index derivatives session is closed or the contract has expired.");
        }
    }

    LocalDate today() { return clock.instant().atZone(INDIA_ZONE).toLocalDate(); }

    private Wallet lockWallet(UUID user) {
        return db.query("SELECT cash, realized_pnl FROM derivative_wallet WHERE user_id=? FOR UPDATE", this::wallet, user)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Virtual wallet is unavailable."));
    }

    private Position findPosition(UUID user, UUID contract) {
        return db.query("SELECT * FROM derivative_position WHERE user_id=? AND contract_id=?", this::position, user, contract)
            .stream().findFirst().orElse(null);
    }

    private Wallet wallet(ResultSet row, int ignored) throws SQLException {
        return new Wallet(row.getBigDecimal("cash"), row.getBigDecimal("realized_pnl"));
    }

    private Position position(ResultSet row, int ignored) throws SQLException {
        return new Position(row.getInt("lots"), Direction.valueOf(row.getString("direction")),
            row.getBigDecimal("average_entry"), row.getBigDecimal("reserved_margin"));
    }

    private Fill fill(ResultSet row, int ignored) throws SQLException {
        return new Fill(row.getObject("id", UUID.class), row.getObject("contract_id", UUID.class), row.getString("symbol"),
            Action.valueOf(row.getString("action")), row.getInt("lots"), row.getBigDecimal("price"), row.getBigDecimal("cash_change"),
            row.getBigDecimal("realized_pnl"), row.getTimestamp("filled_at").toInstant());
    }
}
