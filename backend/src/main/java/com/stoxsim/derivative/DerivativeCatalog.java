package com.stoxsim.derivative;

import static com.stoxsim.calendar.service.IndiaMarketSessionService.INDIA_ZONE;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class DerivativeCatalog {
    public static final Set<String> UNDERLYINGS = Set.of("NSE_INDEX|Nifty 50", "NSE_INDEX|Nifty Bank");

    public enum Kind { FUT, CE, PE }

    public record Contract(UUID id, String providerKey, String underlyingKey, String symbol, Kind kind,
        LocalDate expiry, int lotSize, BigDecimal tickSize, BigDecimal strike) {}

    private final JdbcTemplate db;
    private final boolean enabled;

    public DerivativeCatalog(JdbcTemplate db, @Value("${stoxsim.trading.index-derivatives-enabled:false}") boolean enabled) {
        this.db = db;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    public Optional<Contract> parse(JsonNode node) {
        if (!enabled || !"NSE_FO".equals(node.path("segment").asText())
            || !"NSE".equals(node.path("exchange").asText())
            || !"INDEX".equals(node.path("underlying_type").asText())) return Optional.empty();
        String underlying = node.path("underlying_key").asText();
        String key = node.path("instrument_key").asText();
        String symbol = node.path("trading_symbol").asText();
        int lotSize = node.path("lot_size").asInt(0);
        long expiration = node.path("expiry").asLong(0);
        if (!UNDERLYINGS.contains(underlying) || !key.matches("NSE_FO\\|[0-9]{1,16}")
            || symbol.isBlank() || symbol.length() > 100 || lotSize <= 0 || lotSize > 10_000
            || expiration < 946684800000L || expiration > 4102444800000L) return Optional.empty();
        try {
            Kind kind = Kind.valueOf(node.path("instrument_type").asText());
            BigDecimal tick = new BigDecimal(node.path("tick_size").asText()).movePointLeft(2).stripTrailingZeros();
            BigDecimal strike = kind == Kind.FUT ? null : new BigDecimal(node.path("strike_price").asText()).stripTrailingZeros();
            if (tick.signum() <= 0 || tick.scale() > 6 || tick.compareTo(BigDecimal.TEN) > 0
                || (strike != null && (strike.signum() <= 0 || strike.scale() > 4 || strike.compareTo(BigDecimal.valueOf(1_000_000)) > 0))) return Optional.empty();
            LocalDate expiry = Instant.ofEpochMilli(expiration).atZone(INDIA_ZONE).toLocalDate();
            UUID id = UUID.nameUUIDFromBytes((key + ":" + expiry).getBytes(StandardCharsets.UTF_8));
            return Optional.of(new Contract(id, key, underlying, symbol, kind, expiry, lotSize, tick, strike));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public void upsert(List<Contract> contracts, UUID syncId) {
        if (contracts.isEmpty()) return;
        db.batchUpdate("""
            INSERT INTO derivative_contract(id, provider_key, underlying_key, symbol, kind, expiry, lot_size, tick_size, strike, sync_id, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
            ON CONFLICT (id) DO UPDATE SET symbol=excluded.symbol, sync_id=excluded.sync_id, active=true
            WHERE derivative_contract.underlying_key=excluded.underlying_key
              AND derivative_contract.kind=excluded.kind
              AND derivative_contract.strike IS NOT DISTINCT FROM excluded.strike
              AND derivative_contract.lot_size=excluded.lot_size
              AND derivative_contract.tick_size=excluded.tick_size
            """, contracts, 400, (statement, c) -> {
            statement.setObject(1, c.id()); statement.setString(2, c.providerKey()); statement.setString(3, c.underlyingKey());
            statement.setString(4, c.symbol()); statement.setString(5, c.kind().name()); statement.setObject(6, c.expiry());
            statement.setInt(7, c.lotSize()); statement.setBigDecimal(8, c.tickSize()); statement.setBigDecimal(9, c.strike());
            statement.setObject(10, syncId);
        });
    }

    public void deactivateMissing(UUID syncId) {
        db.update("UPDATE derivative_contract SET active=false WHERE active AND sync_id<>?", syncId);
    }

    public List<LocalDate> expiries(String underlying, LocalDate today) {
        return db.queryForList("""
            SELECT DISTINCT expiry FROM derivative_contract WHERE active AND underlying_key=? AND expiry>=?
            ORDER BY expiry LIMIT 40
            """, LocalDate.class, underlying, today);
    }

    public List<Contract> chain(String underlying, LocalDate expiry, int offset) {
        // Page by strike so a call and put at the same strike remain together.
        return db.query("""
            SELECT * FROM derivative_contract
            WHERE active AND underlying_key=? AND expiry=? AND (kind='FUT' OR strike IN (
                SELECT DISTINCT strike FROM derivative_contract WHERE active AND underlying_key=? AND expiry=? AND strike IS NOT NULL
                ORDER BY strike LIMIT 25 OFFSET ?))
            ORDER BY strike NULLS FIRST, kind, symbol
            """, this::contract, underlying, expiry, underlying, expiry, offset);
    }

    public Optional<Contract> active(UUID id) {
        // The catalog row lock serializes specification refreshes with new positions.
        return db.query("SELECT * FROM derivative_contract WHERE id=? AND active FOR UPDATE", this::contract, id).stream().findFirst();
    }

    public List<Contract> due(LocalDate today) {
        return db.query("""
            SELECT c.* FROM derivative_contract c WHERE c.expiry<?
            AND EXISTS (SELECT 1 FROM derivative_position p WHERE p.contract_id=c.id)
            ORDER BY c.settlement_checked_at NULLS FIRST, c.expiry, c.id LIMIT 12
            """, this::contract, today);
    }

    Contract contract(ResultSet row, int ignored) throws SQLException {
        return new Contract(row.getObject("id", UUID.class), row.getString("provider_key"), row.getString("underlying_key"),
            row.getString("symbol"), Kind.valueOf(row.getString("kind")), row.getObject("expiry", LocalDate.class),
            row.getInt("lot_size"), row.getBigDecimal("tick_size"), row.getBigDecimal("strike"));
    }
}
