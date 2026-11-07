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

    @Transactional(readOnly = true)
    public List<ReflectionResponse> list(UUID user) {
        requireUser(user);
        return db.query("SELECT * FROM decision_reflection WHERE user_id=? ORDER BY created_at DESC,id LIMIT 50", this::map, user);
    }

    @Transactional(readOnly = true)
    public ReflectionResponse get(UUID user, UUID id) {
        requireUser(user);
        return db.query("SELECT * FROM decision_reflection WHERE id=? AND user_id=?", this::map, id, user)
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
        activity.record(user, Kind.REFLECTION_SAVED);
        return get(user, id);
    }

    @Transactional
    public ReflectionResponse update(UUID user, UUID id, ReflectionRequest request) {
        get(user, id);
        UUID account = ownedAccount(user, request);
        int changed = db.update("""
            UPDATE decision_reflection SET account_id=?,order_id=?,reason=?,assumptions=?,risks=?,expected_outcome=?,reflection=?,updated_at=?
            WHERE id=? AND user_id=?
            """, account, request.orderId(), text(request.reason()), text(request.assumptions()), text(request.risks()),
            text(request.expectedOutcome()), text(request.reflection()), Timestamp.from(clock.instant()), id, user);
        if (changed != 1) throw missing();
        activity.record(user, Kind.REFLECTION_SAVED);
        return get(user, id);
    }

    @Transactional
    public void delete(UUID user, UUID id) {
        requireUser(user);
        if (db.update("DELETE FROM decision_reflection WHERE id=? AND user_id=?", id, user) != 1) throw missing();
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
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
