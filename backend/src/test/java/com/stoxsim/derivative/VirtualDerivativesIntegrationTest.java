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
        call = new Contract(UUID.randomUUID(), "NSE_FO|789", "NSE_INDEX|Nifty 50", "NIFTY fixture call", Kind.CE,
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
        order(UUID.randomUUID(), "CLOSE", 1).andExpect(status().isBadRequest());
        clock.time = Instant.parse("2026-11-06T15:00:00Z");
        order(UUID.randomUUID(), "OPEN_LONG", 1).andExpect(status().isBadRequest());
        portfolio().andExpect(jsonPath("$.cash").value(500000)).andExpect(jsonPath("$.fills.length()").value(0));
        mvc.perform(get("/api/v1/derivatives/portfolio")).andExpect(status().isUnauthorized());
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
