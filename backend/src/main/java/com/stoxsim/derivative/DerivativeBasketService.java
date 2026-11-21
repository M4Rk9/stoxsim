package com.stoxsim.derivative;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.derivative.DerivativeBook.Action;
import com.stoxsim.derivative.DerivativeCatalog.Contract;
import com.stoxsim.derivative.DerivativeTradingService.Fill;
import com.stoxsim.derivative.DerivativeTradingService.Preview;
import com.stoxsim.order.service.TradingValidationException;

@Service
public class DerivativeBasketService {
    public record Leg(@NotNull UUID contractId, @NotNull Action action, @Min(1) @Max(100) int lots) {}
    public record BasketPreview(List<Preview> legs, BigDecimal requiredFunds, BigDecimal cashAfter,
        DerivativePayoff.Result payoff, Instant generatedAt) {}
    public record BasketFill(UUID basketId, List<Fill> fills) {}
    private record Existing(UUID id, String signature) {}
    private final JdbcTemplate db;
    private final DerivativeCatalog catalog;
    private final DerivativeTradingService trading;
    private final Clock clock;

    public DerivativeBasketService(JdbcTemplate db, DerivativeCatalog catalog, DerivativeTradingService trading, Clock clock) {
        this.db = db; this.catalog = catalog; this.trading = trading; this.clock = clock;
    }

    private List<Leg> validate(List<Leg> input) {
        if (!catalog.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (input == null || input.size() < 2 || input.size() > 4)
            throw new TradingValidationException("Choose two to four basket legs.");
        var ids = new HashSet<UUID>();
        for (Leg leg : input) {
            if (leg == null || leg.contractId() == null || !ids.add(leg.contractId()) || leg.lots() < 1 || leg.lots() > 100
                || (leg.action() != Action.OPEN_LONG && leg.action() != Action.OPEN_SHORT))
                throw new TradingValidationException("Use distinct contracts, valid lots and opening actions only.");
        }
        return input.stream().sorted(Comparator.comparing(l -> l.contractId().toString())).toList();
    }

    private void requireGroup(List<Leg> legs, boolean lock) {
        Contract first = null;
        for (Leg leg : legs) {
            Contract c = (lock ? catalog.active(leg.contractId()) : catalog.activeSnapshot(leg.contractId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract is unavailable."));
            if (first == null) first = c;
            else if (!first.underlyingKey().equals(c.underlyingKey()) || !first.expiry().equals(c.expiry()))
                throw new TradingValidationException("Basket legs must use the same index and expiry.");
            if (c.kind() != DerivativeCatalog.Kind.FUT && leg.action() == Action.OPEN_SHORT)
                throw new TradingValidationException("Option writing is not supported.");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BasketPreview preview(UUID user, List<Leg> input) {
        List<Leg> legs = validate(input);
        requireGroup(legs, false);
        List<Preview> previews = legs.stream().map(l -> trading.preview(user, l.contractId(), l.action(), l.lots())).toList();
        BigDecimal funds = previews.stream().map(Preview::requiredFunds).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cash = previews.getFirst().cashAfter().subtract(previews.getFirst().cashChange());
        if (cash.compareTo(funds) < 0) throw new TradingValidationException("Insufficient virtual funds for the whole basket.");
        int count = db.queryForObject("SELECT count(*) FROM derivative_position WHERE user_id=?", Integer.class, user);
        for (Leg leg : legs) {
            if (db.queryForObject("SELECT count(*) FROM derivative_position WHERE user_id=? AND contract_id=?",
                Integer.class, user, leg.contractId()) == 0) count++;
        }
        if (count > 50) throw new TradingValidationException("Close an existing position before adding this basket.");
        return new BasketPreview(previews, funds, cash.subtract(funds), DerivativePayoff.calculate(previews), clock.instant());
    }

    @Transactional
    public BasketFill place(UUID user, UUID requestKey, List<Leg> input) {
        List<Leg> legs = validate(input);
        if (requestKey == null) throw new TradingValidationException("A basket request key is required.");
        String signature = legs.stream().map(l -> l.contractId() + ":" + l.action() + ":" + l.lots()).collect(Collectors.joining(";"));
        db.update("INSERT INTO derivative_wallet(user_id,cash) VALUES (?,?) ON CONFLICT (user_id) DO NOTHING", user, DerivativeBook.STARTING_CASH);
        db.queryForObject("SELECT cash FROM derivative_wallet WHERE user_id=? FOR UPDATE", BigDecimal.class, user);
        var previous = db.query("SELECT id, signature FROM derivative_basket WHERE user_id=? AND request_key=?",
            (r, i) -> new Existing(r.getObject("id", UUID.class), r.getString("signature")), user, requestKey);
        if (!previous.isEmpty()) {
            Existing existing = previous.getFirst();
            if (!existing.signature().equals(signature)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Request key already belongs to a different basket.");
            return new BasketFill(existing.id(), trading.basketFills(user, existing.id()));
        }
        // Canonical lock order; keep the wallet lock for all legs and their combined funding.
        requireGroup(legs, true);
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO derivative_basket(id,user_id,request_key,signature,filled_at) VALUES (?,?,?,?,?)",
            id, user, requestKey, signature, java.sql.Timestamp.from(clock.instant()));
        for (Leg leg : legs) {
            UUID legKey = UUID.nameUUIDFromBytes(("basket:" + id + ":" + leg.contractId()).getBytes(StandardCharsets.UTF_8));
            Fill fill = trading.place(user, leg.contractId(), legKey, leg.action(), leg.lots());
            db.update("UPDATE derivative_fill SET basket_id=? WHERE id=? AND user_id=?", id, fill.id(), user);
        }
        // Any leg failure propagates and rolls back wallet, positions, fills and the basket together.
        return new BasketFill(id, trading.basketFills(user, id));
    }
}
