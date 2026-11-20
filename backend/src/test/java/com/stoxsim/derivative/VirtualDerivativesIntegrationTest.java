package com.stoxsim.derivative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.derivative.DerivativeCatalog.Contract;
import com.stoxsim.derivative.DerivativeCatalog.Kind;
import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.upstox.UpstoxDerivativeQuotes;
import com.stoxsim.market.provider.upstox.UpstoxHistoricalCandleClient;

@Testcontainers
@SpringBootTest(properties = {
    "stoxsim.trading.index-derivatives-enabled=true",
    "stoxsim.market-data.upstox.public-serving-enabled=false",
    "stoxsim.market-data.alpaca.public-serving-enabled=false",
    "stoxsim.portfolio.history.enabled=false"
})
class VirtualDerivativesIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Container @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired AppUserRepository users;
    @Autowired DerivativeCatalog catalog;
    @Autowired DerivativeExpiryJob expiryJob;
    @Autowired TestClock clock;
    @Autowired DerivativeBasketService baskets;
    @Autowired com.stoxsim.auth.service.AccountLifecycleService lifecycle;
    @MockitoBean UpstoxDerivativeQuotes quotes;
    @MockitoBean UpstoxHistoricalCandleClient candles;

    private static final Instant REGULAR = Instant.parse("2026-11-06T06:00:00Z");
    private static final LocalDate EXPIRY = LocalDate.of(2026, 11, 6);
    private MockMvc mvc;
    private UUID user;
    private Contract call;

    @TestConfiguration
    static class Time {
        @Bean @Primary TestClock derivativeTestClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        private volatile Instant time = REGULAR;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(time, zone); }
        @Override public Instant instant() { return time; }
    }

    @BeforeEach
    void setup() {
        clock.time = REGULAR;
        db.execute("TRUNCATE app_user, derivative_contract CASCADE");
        user = users.saveAndFlush(new AppUser("derivative@test.local", "hash", "Derivative Learner")).getId();
        call = new Contract(UUID.fromString("11111111-1111-4111-8111-111111111111"), "NSE_FO|789", "NSE_INDEX|Nifty 50", "NIFTY fixture call", Kind.CE,
            EXPIRY, 65, new BigDecimal("0.05"), new BigDecimal("24000"));
        catalog.upsert(List.of(call), UUID.randomUUID());
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        quote("80");
    }

    @Test
    void authenticatedFillsReconcileAcrossPartialCloseRetriesAndExpiry() throws Exception {
        UUID key = UUID.randomUUID();
        order(key, "OPEN_LONG", 2).andExpect(status().isOk()).andExpect(jsonPath("$.cashChange").value(-10400));
        order(key, "OPEN_LONG", 2).andExpect(status().isOk());
        order(key, "OPEN_LONG", 1).andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isEqualTo(1);
        quote("90");
        order(UUID.randomUUID(), "CLOSE", 1).andExpect(status().isOk())
            .andExpect(jsonPath("$.realizedPnl").value(650));
        portfolio().andExpect(status().isOk()).andExpect(jsonPath("$.positions[0].lots").value(1))
            .andExpect(jsonPath("$.cash").value(495450));
        assertThat(db.queryForObject("SELECT count(*) FROM paper_order", Integer.class)).isZero();

        clock.time = Instant.parse("2026-11-07T02:00:00Z");
        expiryJob.settleExpired();
        portfolio().andExpect(jsonPath("$.positions[0].awaitingSettlement").value(true));
        BigDecimal close = new BigDecimal("24100");
        when(candles.getCandles(call.underlyingKey(), "days", 1, EXPIRY, EXPIRY))
            .thenReturn(List.of(new Candle(REGULAR, close, close, close, close, 100L)));
        expiryJob.settleExpired();
        expiryJob.settleExpired();
        portfolio().andExpect(jsonPath("$.positions.length()").value(0)).andExpect(jsonPath("$.cash").value(501950))
            .andExpect(jsonPath("$.realizedPnl").value(1950));
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill WHERE action='EXPIRY'", Integer.class)).isEqualTo(1);
    }

    @Test
    void staleQuotesClosedSessionsAndMissingLotsDoNotChangeBalances() throws Exception {
        when(quotes.isFresh(any())).thenReturn(false);
        order(UUID.randomUUID(), "OPEN_LONG", 1).andExpect(status().isServiceUnavailable());
        quote("80");
        order(UUID.randomUUID(), "OPEN_LONG", 0).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.lots").exists());
        order(UUID.randomUUID(), "CLOSE", 1).andExpect(status().is(422))
            .andExpect(jsonPath("$.message").value("Not enough open lots to close."));
        order(UUID.randomUUID(), "OPEN_LONG", 100).andExpect(status().is(422))
            .andExpect(jsonPath("$.message").value("Insufficient virtual funds for premium or futures margin."));
        clock.time = Instant.parse("2026-11-06T15:00:00Z");
        order(UUID.randomUUID(), "OPEN_LONG", 1).andExpect(status().is(422))
            .andExpect(jsonPath("$.message").value("The regular index derivatives session is closed or the contract has expired."));
        portfolio().andExpect(jsonPath("$.cash").value(500000)).andExpect(jsonPath("$.fills.length()").value(0));
        mvc.perform(get("/api/v1/derivatives/portfolio")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOpeningContractsWithUnverifiedExpiryDates() throws Exception {
        call = new Contract(UUID.randomUUID(), "NSE_FO|790", call.underlyingKey(), "NIFTY later call", Kind.CE,
            LocalDate.of(2099, 1, 5), call.lotSize(), call.tickSize(), call.strike());
        catalog.upsert(List.of(call), UUID.randomUUID());
        order(UUID.randomUUID(), "OPEN_LONG", 1).andExpect(status().is(422))
            .andExpect(jsonPath("$.message").value("The exchange session for this contract's expiry is unverified or closed."));
        verify(quotes, never()).snapshots(List.of(call.providerKey()));
        portfolio().andExpect(jsonPath("$.cash").value(500000)).andExpect(jsonPath("$.positions.length()").value(0));
    }

    @Test
    void previewMatchesExecutionWithoutCreatingAWalletOrFill() throws Exception {
        preview("OPEN_LONG", 2).andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(130))
            .andExpect(jsonPath("$.price").value(80))
            .andExpect(jsonPath("$.requiredFunds").value(10400))
            .andExpect(jsonPath("$.cashAfter").value(489600))
            .andExpect(jsonPath("$.maximumLoss").value(10400))
            .andExpect(jsonPath("$.breakeven").value(24080))
            .andExpect(jsonPath("$.unlimitedLoss").value(false));
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_wallet", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isZero();
        order(UUID.randomUUID(), "OPEN_LONG", 2).andExpect(jsonPath("$.cashChange").value(-10400));
        quote("90");
        preview("CLOSE", 1).andExpect(status().isOk())
            .andExpect(jsonPath("$.cashChange").value(5850))
            .andExpect(jsonPath("$.realizedPnl").value(650))
            .andExpect(jsonPath("$.maximumLoss").isEmpty());
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isEqualTo(1);
    }

    @Test
    void futuresPreviewSeparatesMarginFromFullExposureAndShortUnlimitedLoss() throws Exception {
        call = new Contract(UUID.randomUUID(), "NSE_FO|791", call.underlyingKey(), "NIFTY fixture future",
            Kind.FUT, EXPIRY, 65, new BigDecimal("0.05"), null);
        catalog.upsert(List.of(call), UUID.randomUUID());
        quote("24000");
        preview("OPEN_LONG", 1).andExpect(status().isOk())
            .andExpect(jsonPath("$.requiredFunds").value(312000))
            .andExpect(jsonPath("$.maximumLoss").value(1560000))
            .andExpect(jsonPath("$.breakeven").value(24000));
        preview("OPEN_SHORT", 1).andExpect(status().isOk())
            .andExpect(jsonPath("$.requiredFunds").value(312000))
            .andExpect(jsonPath("$.maximumLoss").isEmpty())
            .andExpect(jsonPath("$.unlimitedLoss").value(true));
        preview("OPEN_LONG", 2).andExpect(status().is(422));
    }

    @Test
    void putPreviewUsesStrikeMinusPremiumAndCannotWriteOptions() throws Exception {
        call = new Contract(UUID.randomUUID(), "NSE_FO|792", call.underlyingKey(), "NIFTY fixture put",
            Kind.PE, EXPIRY, 65, new BigDecimal("0.05"), new BigDecimal("24000"));
        catalog.upsert(List.of(call), UUID.randomUUID());
        quote("80");
        preview("OPEN_LONG", 1).andExpect(status().isOk())
            .andExpect(jsonPath("$.breakeven").value(23920))
            .andExpect(jsonPath("$.maximumLoss").value(5200));
        preview("OPEN_SHORT", 1).andExpect(status().is(422));
    }

    @Test
    void previewUsesExecutableSideAndTickRoundingRatherThanLastTrade() throws Exception {
        var key = new InstrumentKey("UPSTOX", call.providerKey(), MarketRegion.INDIA);
        when(quotes.snapshots(List.of(call.providerKey()))).thenReturn(Map.of(call.providerKey(),
            new Quote(key, new BigDecimal("80"), new BigDecimal("79.97"), new BigDecimal("81.03"),
                null, null, null, null, null, 10L, REGULAR, REGULAR)));
        preview("OPEN_LONG", 1).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(81.05))
            .andExpect(jsonPath("$.requiredFunds").value(5268.25));
        order(UUID.randomUUID(), "OPEN_LONG", 1).andExpect(jsonPath("$.price").value(81.05));
        preview("CLOSE", 1).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(79.95))
            .andExpect(jsonPath("$.realizedPnl").value(-71.5));
    }

    @Test
    void previewRequiresAuthenticationOwnPositionAndFreshExecutableQuote() throws Exception {
        mvc.perform(post("/api/v1/derivatives/preview").contentType(MediaType.APPLICATION_JSON)
            .content("{\"contractId\":\"" + call.id() + "\",\"action\":\"OPEN_LONG\",\"lots\":1}"))
            .andExpect(status().isUnauthorized());
        preview("OPEN_LONG", 0).andExpect(status().isBadRequest());
        preview("EXPIRY", 1).andExpect(status().is(422));
        order(UUID.randomUUID(), "OPEN_LONG", 1).andExpect(status().isOk());
        user = users.saveAndFlush(new AppUser("other-derivative@test.local", "hash", "Other")).getId();
        preview("CLOSE", 1).andExpect(status().is(422));
        when(quotes.isFresh(any())).thenReturn(false);
        preview("OPEN_LONG", 1).andExpect(status().isServiceUnavailable());
        quote("80");
        clock.time = Instant.parse("2026-11-06T15:00:00Z");
        preview("OPEN_LONG", 1).andExpect(status().is(422));
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isEqualTo(1);
    }

    private ResultActions preview(String action, int lots) throws Exception {
        return mvc.perform(post("/api/v1/derivatives/preview").with(jwt().jwt(j -> j.subject(user.toString())))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"contractId":"%s","action":"%s","lots":%d}
                """.formatted(call.id(), action, lots)));
    }

    private Contract basketPut() {
        Contract put = new Contract(UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"), "NSE_FO|790",
            call.underlyingKey(), "NIFTY fixture put", Kind.PE, EXPIRY, 65, new BigDecimal("0.05"), call.strike());
        catalog.upsert(List.of(call, put), UUID.randomUUID());
        BigDecimal price = new BigDecimal("80");
        when(quotes.snapshots(List.of(put.providerKey()))).thenReturn(Map.of(put.providerKey(),
            new Quote(new InstrumentKey("UPSTOX", put.providerKey(), MarketRegion.INDIA), price, price, price,
                null, null, null, null, null, 10L, REGULAR, REGULAR)));
        return put;
    }

    private String basketBody(UUID key, Contract put, int lots) {
        return """
            {"requestKey":"%s","legs":[{"contractId":"%s","action":"OPEN_LONG","lots":%d},
            {"contractId":"%s","action":"OPEN_LONG","lots":%d}]}
            """.formatted(key, call.id(), lots, put.id(), lots);
    }

    private ResultActions basketRequest(String endpoint, String body) throws Exception {
        return mvc.perform(post("/api/v1/derivatives/baskets/" + endpoint).with(jwt().jwt(j -> j.subject(user.toString())))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test void basketPreviewIsReadOnlyAndRepeatedExecutionHasExactlyTwoFills() throws Exception {
        Contract put = basketPut();
        UUID key = UUID.randomUUID();
        String body = basketBody(key, put, 1);
        basketRequest("preview", body).andExpect(status().isOk())
            .andExpect(jsonPath("$.requiredFunds").value(10400))
            .andExpect(jsonPath("$.cashAfter").value(489600))
            .andExpect(jsonPath("$.payoff.maximumLoss").value(10400));
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_wallet", Integer.class)).isZero();
        basketRequest("orders", body).andExpect(status().isOk()).andExpect(jsonPath("$.fills.length()").value(2))
            .andExpect(jsonPath("$.fills[0].basketId").isNotEmpty());
        basketRequest("orders", body).andExpect(status().isOk());
        basketRequest("orders", basketBody(key, put, 2)).andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isEqualTo(2);
        portfolio().andExpect(jsonPath("$.cash").value(489600));
        var export = lifecycle.exportAccount(user);
        assertThat((List<?>) export.get("derivativesBaskets")).hasSize(1);
        db.update("DELETE FROM app_user WHERE id=?", user);
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_basket", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isZero();
    }

    @Test void laterLegFailureRollsBackEarlierFillWalletAndBasket() throws Exception {
        Contract put = basketPut();
        // Canonical ordering ensures the call executes first.
        when(quotes.snapshots(List.of(put.providerKey()))).thenReturn(Map.of());
        when(quotes.isFresh(isNull())).thenReturn(false);
        basketRequest("orders", basketBody(UUID.randomUUID(), put, 1)).andExpect(status().isServiceUnavailable());
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_wallet", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_basket", Integer.class)).isZero();
    }

    @Test void basketRejectsCombinedFundingAndUnsupportedComposition() throws Exception {
        Contract put = basketPut();
        String expensive = basketBody(UUID.randomUUID(), put, 50); // Each leg fits; both together do not.
        basketRequest("preview", expensive).andExpect(status().is(422));
        basketRequest("orders", expensive).andExpect(status().is(422));
        basketRequest("preview", basketBody(UUID.randomUUID(), put, 1).replace(put.id().toString(), call.id().toString()))
            .andExpect(status().is(422));
        basketRequest("orders", basketBody(UUID.randomUUID(), put, 1).replace("OPEN_LONG", "OPEN_SHORT"))
            .andExpect(status().is(422));
        basketRequest("preview", basketBody(UUID.randomUUID(), put, 1).replace("OPEN_LONG", "CLOSE"))
            .andExpect(status().is(422));
        basketRequest("preview", basketBody(UUID.randomUUID(), put, 0)).andExpect(status().isBadRequest());
        db.update("UPDATE derivative_contract SET underlying_key='NSE_INDEX|Nifty Bank' WHERE id=?", put.id());
        basketRequest("orders", basketBody(UUID.randomUUID(), put, 1)).andExpect(status().is(422));
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isZero();
    }

    @Test void concurrentBasketRetriesRemainUserScopedAndExecuteOnce() throws Exception {
        Contract put = basketPut();
        UUID key = UUID.randomUUID();
        var legs = List.of(new DerivativeBasketService.Leg(call.id(), DerivativeBook.Action.OPEN_LONG, 1),
            new DerivativeBasketService.Leg(put.id(), DerivativeBook.Action.OPEN_LONG, 1));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> baskets.place(user, key, legs));
            var b = executor.submit(() -> baskets.place(user, key, legs.reversed()));
            assertThat(a.get(20, java.util.concurrent.TimeUnit.SECONDS).basketId())
                .isEqualTo(b.get(20, java.util.concurrent.TimeUnit.SECONDS).basketId());
        }
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_fill", Integer.class)).isEqualTo(2);
        UUID other = users.saveAndFlush(new AppUser("basket-other@test.local", "hash", "Other")).getId();
        baskets.place(other, key, legs);
        assertThat(db.queryForObject("SELECT count(*) FROM derivative_basket", Integer.class)).isEqualTo(2);
        mvc.perform(post("/api/v1/derivatives/baskets/orders").contentType(MediaType.APPLICATION_JSON)
            .content(basketBody(key, put, 1))).andExpect(status().isUnauthorized());
    }

    private void quote(String value) {
        BigDecimal price = new BigDecimal(value);
        var key = new InstrumentKey("UPSTOX", call.providerKey(), MarketRegion.INDIA);
        when(quotes.snapshots(List.of(call.providerKey()))).thenReturn(Map.of(call.providerKey(),
            new Quote(key, price, price, price, null, null, null, null, null, 10L, REGULAR, REGULAR)));
        when(quotes.isFresh(any())).thenReturn(true);
        when(quotes.snapshots(List.of())).thenReturn(Map.of());
    }

    private ResultActions order(UUID key, String action, int lots) throws Exception {
        return mvc.perform(post("/api/v1/derivatives/orders").with(jwt().jwt(j -> j.subject(user.toString())))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"requestKey":"%s","contractId":"%s","action":"%s","lots":%d}
                """.formatted(key, call.id(), action, lots)));
    }

    private ResultActions portfolio() throws Exception {
        return mvc.perform(get("/api/v1/derivatives/portfolio").with(jwt().jwt(j -> j.subject(user.toString()))));
    }
}
