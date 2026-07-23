package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.OrderStatus;
import com.stoxsim.order.domain.OrderType;
import com.stoxsim.order.domain.PaperOrder;
import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.order.service.TradingValidationException;
import com.stoxsim.watchlist.domain.Watchlist;
import com.stoxsim.watchlist.domain.WatchlistItem;
import com.stoxsim.watchlist.repository.WatchlistItemRepository;
import com.stoxsim.watchlist.repository.WatchlistRepository;

@Testcontainers
@SpringBootTest(properties = {
    "stoxsim.market-data.upstox.stream-enabled=false",
    "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "spring.task.scheduling.enabled=false"
})
class PostgresConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private AppUserRepository users;
    @Autowired private VirtualAccountRepository accounts;
    @Autowired private TradableInstrumentRepository instruments;
    @Autowired private PaperOrderRepository orders;
    @Autowired private WatchlistRepository watchlists;
    @Autowired private WatchlistItemRepository watchlistItems;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
            TRUNCATE TABLE
                watchlist_item,
                watchlist,
                account_ledger,
                trade,
                paper_order,
                holding,
                virtual_account,
                app_user,
                instrument
            RESTART IDENTITY CASCADE
            """);
    }

    @Test
    void flywaySchemaPersistsWatchlistsAndRejectsDuplicateMembership() {
        AppUser user = user("watchlist");
        TradableInstrument instrument = instrument("RELIANCE", "NSE_EQ|INE002A01018");
        Watchlist watchlist = watchlists.save(new Watchlist(user, "My Watchlist", true));
        watchlistItems.saveAndFlush(new WatchlistItem(watchlist, instrument));

        var saved = watchlistItems.findAllByWatchlistIdOrderByCreatedAtDesc(watchlist.getId());

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getInstrument().getTradingSymbol()).isEqualTo("RELIANCE");
        assertThatThrownBy(() -> watchlistItems.saveAndFlush(
            new WatchlistItem(watchlist, instrument)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountLockPreventsTwoOrdersFromSpendingTheSameCash() throws Exception {
        AppUser user = user("cash-lock");
        VirtualAccount account = accounts.save(new VirtualAccount(
            user,
            MarketRegion.INDIA,
            new BigDecimal("100.00")
        ));

        List<Boolean> results = runConcurrently(() -> {
            try {
                return transactions.execute(status -> {
                    VirtualAccount locked = accounts.findByIdForUpdate(account.getId()).orElseThrow();
                    locked.reserveCash(new BigDecimal("80.00"));
                    return true;
                });
            } catch (TradingValidationException expected) {
                return false;
            }
        });

        assertThat(results).containsExactlyInAnyOrder(true, false);
        VirtualAccount reloaded = accounts.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getAvailableCash()).isEqualByComparingTo("20.0000");
        assertThat(reloaded.getBlockedCash()).isEqualByComparingTo("80.0000");
    }

    @Test
    void orderLockAllowsOnlyOneExecutionTransition() throws Exception {
        AppUser user = user("order-lock");
        VirtualAccount account = accounts.save(new VirtualAccount(
            user,
            MarketRegion.INDIA,
            new BigDecimal("500000.00")
        ));
        TradableInstrument instrument = instrument("TCS", "NSE_EQ|INE467B01029");
        PaperOrder order = orders.save(new PaperOrder(
            account,
            instrument,
            "concurrent-fill",
            OrderSide.BUY,
            OrderType.LIMIT,
            1,
            new BigDecimal("3500.00"),
            new BigDecimal("3500.00"),
            LocalDate.now()
        ));

        List<Boolean> results = runConcurrently(() -> transactions.execute(status -> {
            PaperOrder locked = orders.findByIdForUpdate(order.getId()).orElseThrow();
            if (!locked.isOpen()) {
                return false;
            }
            locked.markExecuted(
                new BigDecimal("3499.95"),
                new BigDecimal("3499.95"),
                Instant.now()
            );
            return true;
        }));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.EXECUTED);
    }

    private List<Boolean> runConcurrently(Callable<Boolean> operation) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> synchronizedOperation = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start in time");
                }
                return operation.call();
            };
            Future<Boolean> first = executor.submit(synchronizedOperation);
            Future<Boolean> second = executor.submit(synchronizedOperation);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private AppUser user(String prefix) {
        return users.save(new AppUser(
            prefix + "-" + UUID.randomUUID() + "@stoxsim.test",
            "integration-test-password-hash",
            "Integration Test"
        ));
    }

    private TradableInstrument instrument(String symbol, String instrumentKey) {
        return instruments.save(new TradableInstrument(
            new InstrumentSnapshot(
                "UPSTOX",
                instrumentKey,
                MarketRegion.INDIA,
                MarketExchange.NSE,
                "NSE_EQ",
                symbol,
                symbol + " Limited",
                null,
                InstrumentType.EQUITY,
                "INR",
                1,
                new BigDecimal("0.05"),
                "NORMAL"
            ),
            UUID.randomUUID(),
            Instant.now()
        ));
    }
}
