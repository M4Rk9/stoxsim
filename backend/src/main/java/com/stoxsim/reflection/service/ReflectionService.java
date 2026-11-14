package com.stoxsim.reflection.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.analytics.service.ProductActivityEvent.Kind;
import com.stoxsim.analytics.service.ProductActivityService;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;
import com.stoxsim.reflection.api.ReflectionRequest;
import com.stoxsim.reflection.api.StrategyPlan;
import com.stoxsim.reflection.api.ReflectionResponse;

@Service
public class ReflectionService {
    private final JdbcTemplate db;
    private final AppUserRepository users;
    private final ProductActivityService activity;
    private final Clock clock;

    public ReflectionService(JdbcTemplate db, AppUserRepository users, ProductActivityService activity, Clock clock) {
        this.db = db; this.users = users; this.activity = activity; this.clock = clock;
    }

    private static final String SELECT = """
        SELECT r.*, o.status AS linked_status, o.side AS linked_side, o.quantity AS linked_quantity,
            o.execution_price AS linked_price, o.executed_at AS linked_executed_at,
            i.currency AS linked_currency, i.trading_symbol AS linked_symbol, i.exchange AS linked_exchange
        FROM decision_reflection r LEFT JOIN paper_order o ON o.id=r.order_id AND o.account_id=r.account_id
        LEFT JOIN instrument i ON i.id=o.instrument_id
        """;

    @Transactional(readOnly = true)
    public List<ReflectionResponse> list(UUID user) {
        requireUser(user);
        return db.query(SELECT + " WHERE r.user_id=? ORDER BY r.reviewed_at NULLS FIRST,r.review_on NULLS LAST,r.created_at DESC,r.id LIMIT 50", this::map, user);
    }

    @Transactional(readOnly = true)
    public ReflectionResponse get(UUID user, UUID id) {
        requireUser(user);
        return db.query(SELECT + " WHERE r.id=? AND r.user_id=?", this::map, id, user)
            .stream().findFirst().orElseThrow(this::missing);
    }

    @Transactional
    public ReflectionResponse create(UUID user, ReflectionRequest request) {
        // Serializing creation per user makes the storage cap effective under concurrent writes.
        users.findByIdForUpdate(user).orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (db.queryForObject("SELECT count(*) FROM decision_reflection WHERE user_id=?", Integer.class, user) >= 1000)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reflection storage limit reached; export or delete an older reflection first");
        UUID account = ownedAccount(user, request);
        UUID id = UUID.randomUUID();
        var now = Timestamp.from(clock.instant());
        db.update("""
            INSERT INTO decision_reflection(id,user_id,account_id,order_id,reason,assumptions,risks,expected_outcome,reflection,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """, id, user, account, request.orderId(), text(request.reason()), text(request.assumptions()),
            text(request.risks()), text(request.expectedOutcome()), text(request.reflection()), now, now);
        savePlan(user, id, request);
        activity.record(user, Kind.REFLECTION_SAVED);
        return get(user, id);
    }

    @Transactional
    public ReflectionResponse update(UUID user, UUID id, ReflectionRequest request) {
        lock(user, id);
        UUID account = ownedAccount(user, request);
        int changed = db.update("""
            UPDATE decision_reflection SET account_id=?,order_id=?,reason=?,assumptions=?,risks=?,expected_outcome=?,reflection=?,updated_at=?
            WHERE id=? AND user_id=?
            """, account, request.orderId(), text(request.reason()), text(request.assumptions()), text(request.risks()),
            text(request.expectedOutcome()), text(request.reflection()), Timestamp.from(clock.instant()), id, user);
        if (changed != 1) throw missing();
        savePlan(user, id, request);
        activity.record(user, Kind.REFLECTION_SAVED);
        return get(user, id);
    }

    @Transactional
    public void delete(UUID user, UUID id) {
        requireUser(user);
        if (db.update("DELETE FROM decision_reflection WHERE id=? AND user_id=?", id, user) != 1) throw missing();
    }

    private void lock(UUID user, UUID id) {
        requireUser(user);
        if (db.queryForList("SELECT id FROM decision_reflection WHERE id=? AND user_id=? FOR UPDATE", id, user).isEmpty()) throw missing();
    }

    private void savePlan(UUID user, UUID id, ReflectionRequest request) {
        if (request.plan() != null) {
            var p = request.plan();
            if (text(p.exchange()).isEmpty() != text(p.symbol()).isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose both a symbol and exchange, or leave both empty");
            if (p.reviewOn() != null && (p.reviewOn().getYear() < 1970 || p.reviewOn().getYear() > 2100))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a review date between 1970 and 2100");
            db.update("""
                UPDATE decision_reflection SET strategy_exchange=?,strategy_symbol=?,horizon=?,target=?,invalidation=?,review_on=?
                WHERE id=? AND user_id=?
                """, text(p.exchange()), text(p.symbol()), text(p.horizon()), text(p.target()), text(p.invalidation()), p.reviewOn(), id, user);
            if (db.update("UPDATE decision_reflection SET strategy_created_at=? WHERE id=? AND user_id=? AND strategy_created_at IS NULL",
                Timestamp.from(clock.instant()), id, user) == 1) activity.record(user, Kind.STRATEGY_CREATED);
        }
        // Linking a note to a different instrument must not silently change its thesis.
        var note = get(user, id);
        if (note.orderId() != null && !note.plan().symbol().isEmpty()) validateInstrument(user, note.orderId(), note.plan());
        if (Boolean.TRUE.equals(request.completeReview())) {
            if (text(request.reflection()).isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write what happened and what you learned before completing a review");
            int changed = db.update("UPDATE decision_reflection SET reviewed_at=? WHERE id=? AND user_id=? AND reviewed_at IS NULL",
                Timestamp.from(clock.instant()), id, user);
            if (changed == 1) activity.record(user, Kind.REVIEW_COMPLETED);
        }
    }

    @Transactional
    public ReflectionResponse linkOrder(UUID user, UUID id, UUID orderId) {
        lock(user, id);
        var note = get(user, id);
        if (note.orderId() != null && !note.orderId().equals(orderId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This strategy already has a linked order");
        UUID account = ownedAccount(user, new ReflectionRequest(null, orderId, note.reason(), null, null, null, null));
        if (!note.plan().symbol().isEmpty()) validateInstrument(user, orderId, note.plan());
        db.update("UPDATE decision_reflection SET account_id=?,order_id=?,updated_at=? WHERE id=? AND user_id=?",
            account, orderId, Timestamp.from(clock.instant()), id, user);
        return get(user, id);
    }

    private void validateInstrument(UUID user, UUID orderId, StrategyPlan plan) {
        Boolean matches = db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM paper_order o JOIN virtual_account a ON a.id=o.account_id
            JOIN instrument i ON i.id=o.instrument_id
            WHERE o.id=? AND a.user_id=? AND i.exchange=? AND i.trading_symbol=?)
            """, Boolean.class, orderId, user, plan.exchange(), plan.symbol());
        if (!Boolean.TRUE.equals(matches)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The order must match the strategy's symbol and exchange");
    }

    private UUID ownedAccount(UUID user, ReflectionRequest request) {
        UUID account = request.accountId();
        if (request.orderId() != null) {
            UUID orderAccount = db.query("""
                SELECT o.account_id FROM paper_order o JOIN virtual_account a ON a.id=o.account_id
                WHERE o.id=? AND a.user_id=?
                """, (rs, row) -> rs.getObject(1, UUID.class), request.orderId(), user)
                .stream().findFirst().orElseThrow(this::missing);
            if (account != null && !account.equals(orderAccount))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order and account do not match");
            account = orderAccount;
        }
        if (account != null && !Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM virtual_account WHERE id=? AND user_id=?)", Boolean.class, account, user))) throw missing();
        return account;
    }

    private void requireUser(UUID user) {
        if (!users.existsById(user)) throw new UnauthorizedException("User no longer exists");
    }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reflection or linked simulator record not found");
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private ReflectionResponse map(ResultSet rs, int row) throws SQLException {
        return new ReflectionResponse(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
            rs.getObject("order_id", UUID.class), rs.getString("reason"), rs.getString("assumptions"),
            rs.getString("risks"), rs.getString("expected_outcome"), rs.getString("reflection"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
            new StrategyPlan(rs.getString("strategy_exchange"), rs.getString("strategy_symbol"), rs.getString("horizon"),
                rs.getString("target"), rs.getString("invalidation"), rs.getObject("review_on", java.time.LocalDate.class)),
            rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
            rs.getString("linked_status") == null ? null : new ReflectionResponse.LinkedOrder(
                rs.getString("linked_status"), rs.getString("linked_side"), rs.getLong("linked_quantity"),
                rs.getBigDecimal("linked_price"), rs.getString("linked_currency"), rs.getString("linked_exchange"), rs.getString("linked_symbol"),
                rs.getTimestamp("linked_executed_at") == null ? null : rs.getTimestamp("linked_executed_at").toInstant()));
    }
}
