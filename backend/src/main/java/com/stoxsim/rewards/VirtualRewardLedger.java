package com.stoxsim.rewards;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.stoxsim.market.domain.MarketRegion;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class VirtualRewardLedger {
    static final BigDecimal INR = new BigDecimal("100000");
    static final BigDecimal USD = new BigDecimal("1000");
    private final JdbcTemplate db;

    public VirtualRewardLedger(JdbcTemplate db) { this.db = db; }

    record Entry(UUID id, String source, Instant createdAt, BigDecimal inr, BigDecimal usd) { }
    record Totals(BigDecimal inr, BigDecimal usd) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID grantReferral(UUID attribution, UUID owner, UUID reviewer) {
        UUID transaction = UUID.randomUUID();
        db.update("INSERT INTO virtual_reward_transaction(id,source,source_id,actor_id) VALUES (?,'REFERRAL',?,?)",
            transaction, attribution, reviewer);
        credit(transaction, owner, MarketRegion.INDIA, INR);
        credit(transaction, owner, MarketRegion.UNITED_STATES, USD);
        return transaction;
    }

    private void credit(UUID transaction, UUID owner, MarketRegion market, BigDecimal amount) {
        UUID account = db.queryForObject("""
            INSERT INTO virtual_account(user_id,market_region,account_kind,sandbox_slot,account_label,
                currency,available_cash,starting_capital,active,leaderboard_eligible)
            VALUES (?,?,'REWARD',0,'Reward practice',?,?,?,true,false)
            ON CONFLICT (user_id,market_region) WHERE account_kind='REWARD' DO UPDATE SET
                available_cash=virtual_account.available_cash+excluded.available_cash,
                starting_capital=virtual_account.starting_capital+excluded.starting_capital,
                active=true,version=virtual_account.version+1,updated_at=now()
            RETURNING id
            """, UUID.class, owner, market.name(), market.currency(), amount, amount);
        db.update("INSERT INTO virtual_reward_entry(transaction_id,currency,account_id,amount) VALUES (?,?,?,?)",
            transaction, market.currency(), account, amount);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID reverseReferral(UUID original, UUID reviewer) {
        var previous = db.queryForList("SELECT id FROM virtual_reward_transaction WHERE reverses_id=?", UUID.class, original);
        if (!previous.isEmpty()) return previous.getFirst();
        UUID reversal = UUID.randomUUID();
        db.update("""
            INSERT INTO virtual_reward_transaction(id,source,source_id,reverses_id,actor_id)
            VALUES (?,'ADMIN_CORRECTION',?,?,?)
            """, reversal, original, original, reviewer);
        int adjusted = db.update("""
            WITH adjusted AS (
                UPDATE virtual_account a SET available_cash=a.available_cash-e.amount,
                    starting_capital=a.starting_capital-e.amount,version=a.version+1,updated_at=now()
                FROM virtual_reward_entry e
                WHERE e.transaction_id=? AND a.id=e.account_id AND a.account_kind='REWARD'
                  AND e.amount>0 AND a.available_cash>=e.amount AND a.starting_capital>=e.amount
                RETURNING a.id,a.currency,e.amount
            )
            INSERT INTO virtual_reward_entry(transaction_id,currency,account_id,amount)
            SELECT ?,currency,id,-amount FROM adjusted
            """, original, reversal);
        if (adjusted != 2) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Both reward accounts must have enough available practice cash to reverse this grant.");
        return reversal;
    }

    List<Entry> entries(UUID owner) {
        return db.query("""
            SELECT t.id,t.source,t.created_at,
                   max(e.amount) FILTER (WHERE e.currency='INR') AS inr,
                   max(e.amount) FILTER (WHERE e.currency='USD') AS usd
            FROM virtual_reward_transaction t JOIN virtual_reward_entry e ON e.transaction_id=t.id
            JOIN virtual_account a ON a.id=e.account_id AND a.account_kind='REWARD'
            WHERE a.user_id=?
            GROUP BY t.id ORDER BY t.created_at DESC,t.id LIMIT 50
            """, (rs,n) -> new Entry(rs.getObject("id",UUID.class),rs.getString("source"),
            rs.getTimestamp("created_at").toInstant(),rs.getBigDecimal("inr"),rs.getBigDecimal("usd")),owner);
    }

    Totals totals(UUID owner) {
        return db.queryForObject("""
            SELECT coalesce(sum(e.amount) FILTER (WHERE e.currency='INR'),0) AS inr,
                   coalesce(sum(e.amount) FILTER (WHERE e.currency='USD'),0) AS usd
            FROM virtual_reward_entry e JOIN virtual_account a ON a.id=e.account_id AND a.account_kind='REWARD'
            WHERE a.user_id=?
            """, (rs,n) -> new Totals(rs.getBigDecimal("inr"),rs.getBigDecimal("usd")),owner);
    }
}
