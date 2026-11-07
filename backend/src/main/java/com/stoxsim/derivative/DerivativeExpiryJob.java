package com.stoxsim.derivative;

import static com.stoxsim.calendar.service.IndiaMarketSessionService.INDIA_ZONE;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.derivative.DerivativeCatalog.Contract;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.data.Candle;
import com.stoxsim.market.provider.upstox.UpstoxHistoricalCandleClient;

@Component
class DerivativeExpiryJob {
    private static final Logger LOG = LoggerFactory.getLogger(DerivativeExpiryJob.class);
    private final DerivativeTradingService trading;
    private final DerivativeCatalog catalog;
    private final JdbcTemplate db;
    private final UpstoxHistoricalCandleClient candles;
    private final IndiaMarketSessionService sessions;
    private final TransactionTemplate transaction;

    DerivativeExpiryJob(DerivativeTradingService trading, DerivativeCatalog catalog, JdbcTemplate db,
        UpstoxHistoricalCandleClient candles, IndiaMarketSessionService sessions, PlatformTransactionManager transactions) {
        this.trading = trading;
        this.catalog = catalog;
        this.db = db;
        this.candles = candles;
        this.sessions = sessions;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void settleExpired() {
        // Existing positions remain eligible for expiry processing after new trading is disabled.
        var prices = new HashMap<String, Optional<BigDecimal>>();
        for (Contract contract : catalog.due(trading.today())) {
            db.update("UPDATE derivative_contract SET settlement_checked_at=now() WHERE id=?", contract.id());
            try {
                BigDecimal price = db.queryForObject("SELECT settlement_price FROM derivative_contract WHERE id=?",
                    BigDecimal.class, contract.id());
                if (price == null) {
                    String key = contract.underlyingKey() + "@" + contract.expiry();
                    price = prices.computeIfAbsent(key, ignored -> settlementClose(contract)).orElse(null);
                    if (price == null) continue;
                    db.update("UPDATE derivative_contract SET settlement_price=? WHERE id=? AND settlement_price IS NULL", price, contract.id());
                    price = db.queryForObject("SELECT settlement_price FROM derivative_contract WHERE id=?",
                        BigDecimal.class, contract.id());
                }
                BigDecimal close = price;
                var owners = db.queryForList("""
                    SELECT user_id FROM derivative_position WHERE contract_id=? ORDER BY user_id LIMIT 25
                    """, UUID.class, contract.id());
                for (UUID user : owners) {
                    try {
                        transaction.executeWithoutResult(status -> trading.settle(user, contract, close));
                    } catch (RuntimeException exception) {
                        LOG.warn("Virtual derivatives expiry wallet update will retry for contract {}", contract.id(), exception);
                    }
                }
            } catch (RuntimeException exception) {
                LOG.warn("Virtual derivatives expiry processing will retry for contract {}", contract.id(), exception);
            }
        }
    }

    private Optional<BigDecimal> settlementClose(Contract contract) {
        if (!sessions.isCalendarKnown(MarketExchange.NSE, contract.expiry())
            || !sessions.isTradingDay(MarketExchange.NSE, contract.expiry())) return Optional.empty();
        try {
            var bars = candles.getCandles(contract.underlyingKey(), "days", 1, contract.expiry(), contract.expiry()).stream()
                .filter(bar -> bar.timestamp().atZone(INDIA_ZONE).toLocalDate().equals(contract.expiry()))
                .toList();
            if (bars.size() != 1) return Optional.empty();
            Candle bar = bars.getFirst();
            return Optional.ofNullable(bar.close()).filter(close -> close.signum() > 0 && close.compareTo(BigDecimal.valueOf(1_000_000)) <= 0);
        } catch (RuntimeException exception) {
            LOG.warn("Verified virtual derivatives settlement close is unavailable for {}", contract.id());
            return Optional.empty();
        }
    }
}
