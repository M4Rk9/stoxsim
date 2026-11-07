package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.instrument.domain.*;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.cache.MarketDataCache;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;

@Testcontainers
@Transactional
@SpringBootTest(properties={"stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false", "stoxsim.trading.bse-equities-enabled=true"})
class InstrumentSearchIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Container @ServiceConnection(name="redis") static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Autowired TradableInstrumentRepository instruments;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired VirtualAccountRepository accounts;
    @Autowired MarketDataCache marketData;
    MockMvc mvc;

    @TestConfiguration
    static class TradingTime {
        @Bean @Primary Clock tradingClock() {
            return Clock.fixed(Instant.parse("2026-11-06T06:00:00Z"), ZoneOffset.UTC);
        }
    }

    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private TradableInstrument add(String symbol, String name, MarketExchange exchange, InstrumentType type, String isin) {
        boolean us = exchange == MarketExchange.NYSE_ARCA;
        return instruments.saveAndFlush(new TradableInstrument(new InstrumentSnapshot(us ? "ALPACA" : "UPSTOX", UUID.randomUUID().toString(),
            us ? MarketRegion.UNITED_STATES : MarketRegion.INDIA, exchange, us ? "US_EQUITY" : type == InstrumentType.INDEX ? "NSE_INDEX" : exchange.name() + "_EQ",
            symbol, name, isin, type, us ? "USD" : "INR", 1, new BigDecimal("0.05"), "NORMAL"), UUID.randomUUID(), Instant.now()));
    }
    private org.springframework.test.web.servlet.ResultActions search(String region, String query) throws Exception {
        return mvc.perform(get("/api/v1/instruments/search").param("marketRegion", region).param("q", query)
            .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))));
    }
    @Test void exactNseStockWinsAndBseAndIndicesCannotCrowdOutSuggestions() throws Exception {
        add("BSE", "BSE LIMITED", MarketExchange.NSE, InstrumentType.EQUITY, "INE118H01025");
        for (int n=0; n<25; n++) add("A"+n, "BSE index or listing", n%2==0 ? MarketExchange.BSE : MarketExchange.NSE,
            n%2==0 ? InstrumentType.EQUITY : InstrumentType.INDEX, "INE118H01025");
        search("INDIA", "bse").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].tradingSymbol").value("BSE")).andExpect(jsonPath("$[0].exchange").value("NSE"));
    }
    @Test void allowsWebClientsToOptIntoBseAndFilterByExchange() throws Exception {
        add("DUAL", "Dual Fixture", MarketExchange.NSE, InstrumentType.EQUITY, "INE118H01025");
        add("DUAL", "Dual Fixture", MarketExchange.BSE, InstrumentType.EQUITY, "INE118H01025");
        add("DUALONLY", "Dual Fixture BSE-only", MarketExchange.BSE, InstrumentType.EQUITY, "INE040A01034");
        search("INDIA", "dual").andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/instruments/search").param("marketRegion", "INDIA").param("q", "dual")
            .param("includeBse", "true").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/v1/instruments/search").param("marketRegion", "INDIA").param("q", "dual")
            .param("exchange", "BSE").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].exchange").value("BSE")).andExpect(jsonPath("$[1].tradingSymbol").value("DUALONLY"));
    }
    @Test void executesBseOnlyAndDualListingsAgainstSeparateQuotesAndHoldings() throws Exception {
        var nse = add("DUAL", "Dual Fixture", MarketExchange.NSE, InstrumentType.EQUITY, "INE118H01025");
        var bse = add("DUAL", "Dual Fixture", MarketExchange.BSE, InstrumentType.EQUITY, "INE118H01025");
        var bseOnly = add("DUALONLY", "BSE-only Fixture", MarketExchange.BSE, InstrumentType.EQUITY, "INE040A01034");
        var user = new AppUser("bse-orders@test.local", "hash", "BSE Learner");
        user.markEmailVerified();
        users.saveAndFlush(user);
        var account = accounts.saveAndFlush(new VirtualAccount(user, MarketRegion.INDIA, new BigDecimal("500000")));
        quote(nse, 100, false);
        quote(bse, 120, false);
        quote(bseOnly, 80, false);

        mvc.perform(get("/api/v1/instruments/INDIA/BSE/DUAL/quote").with(jwt().jwt(t -> t.subject(user.getId().toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lastPrice").value(120))
            .andExpect(jsonPath("$.instrumentKey").value(bse.getInstrumentKey()));
        order(user, nse, "BUY", 3).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EXECUTED"));
        order(user, bse, "BUY", 4).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EXECUTED"));
        order(user, bseOnly, "BUY", 2).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EXECUTED"));
        order(user, bse, "SELL", 1).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EXECUTED"));
        order(user, bseOnly, "SELL", 1).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EXECUTED"));
        accounts.flush();
        assertThat(quantity(account, nse)).isEqualTo(3);
        assertThat(quantity(account, bse)).isEqualTo(3);
        assertThat(quantity(account, bseOnly)).isEqualTo(1);
        quote(bseOnly, 80, true);
        order(user, bseOnly, "BUY", 1).andExpect(status().isServiceUnavailable());
    }

    private void quote(TradableInstrument instrument, int rupees, boolean simulated) {
        var key = new InstrumentKey("UPSTOX", instrument.getInstrumentKey(), MarketRegion.INDIA);
        var price = BigDecimal.valueOf(rupees);
        Instant now = Instant.now();
        marketData.storeQuote(new Quote(key, price, price, price, null, null, null, null, null, 100L, now, now, simulated));
    }

    private org.springframework.test.web.servlet.ResultActions order(AppUser user, TradableInstrument instrument, String side, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/orders").with(jwt().jwt(token -> token.subject(user.getId().toString())))
            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("""
                {"marketRegion":"INDIA","exchange":"%s","symbol":"%s","side":"%s","orderType":"MARKET","quantity":%d}
                """.formatted(instrument.getExchange(), instrument.getTradingSymbol(), side, quantity)));
    }

    private long quantity(VirtualAccount account, TradableInstrument instrument) {
        return jdbc.queryForObject("SELECT quantity FROM holding WHERE account_id=? AND instrument_id=?", Long.class,
            account.getId(), instrument.getId());
    }
    @Test void ranksSymbolAndNamePrefixesBeforeIncidentalSubstringsAndHandlesSpaces() throws Exception {
        add("ZACME", "Acme Industries", MarketExchange.NSE, InstrumentType.EQUITY, "INE118H01025");
        add("AARDVARK", "Some Acme customer", MarketExchange.NSE, InstrumentType.EQUITY, "INE118H01025");
        add("ACME", "Acme Holding", MarketExchange.NSE, InstrumentType.EQUITY, "INE118H01025");
        add("ACMEX", "Company", MarketExchange.NSE, InstrumentType.EQUITY, "INE118H01025");
        search("INDIA", "acme").andExpect(jsonPath("$[0].tradingSymbol").value("ACME"))
            .andExpect(jsonPath("$[1].tradingSymbol").value("ACMEX")).andExpect(jsonPath("$[2].tradingSymbol").value("ZACME"));
        add("HDFCBANK", "HDFC Bank Limited", MarketExchange.NSE, InstrumentType.EQUITY, "INE040A01034");
        search("INDIA", "hdfc bank").andExpect(jsonPath("$[0].tradingSymbol").value("HDFCBANK"));
    }
    @Test void includesEtfsLimitsResultsAndPreservesSeparateUsMarket() throws Exception {
        add("NIFTYBEES", "NIP IND ETF NIFTY BEES", MarketExchange.NSE, InstrumentType.ETF, "INF204KB14I2");
        for (int n=0; n<12; n++) add("NIFTY"+n, "Nifty ETF "+n, MarketExchange.NSE, InstrumentType.ETF, "INF204KB14I2");
        search("INDIA", "nifty").andExpect(jsonPath("$.length()").value(8));
        search("INDIA", "niftybees").andExpect(jsonPath("$[0].instrumentType").value("ETF"));
        add("SPY", "SPDR S&P 500 ETF", MarketExchange.NYSE_ARCA, InstrumentType.ETF, null);
        search("INDIA", "spy").andExpect(jsonPath("$.length()").value(0));
        search("UNITED_STATES", "spy").andExpect(jsonPath("$[0].exchange").value("NYSE_ARCA"));
    }
    @Test void quarantinesLegacyDebtAndRecognisesRilWithoutWaitingForNextSync() throws Exception {
        var bond = add("747RIL31", "RIL 7.47% 2031 SR Q", MarketExchange.NSE, InstrumentType.EQUITY, "INE002A08708");
        var etf = add("GOLDBEES", "NIP IND ETF GOLD BEES", MarketExchange.NSE, InstrumentType.EQUITY, "INF204KB17I5");
        add("RELIANCE", "RELIANCE INDUSTRIES LTD", MarketExchange.NSE, InstrumentType.EQUITY, "INE002A01018");
        try (var migration = getClass().getResourceAsStream("/db/migration/V121__clean_nse_cash_instruments.sql")) {
            jdbc.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(jdbc.queryForObject("SELECT active FROM instrument WHERE id=?", Boolean.class, bond.getId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT instrument_type FROM instrument WHERE id=?", String.class, etf.getId())).isEqualTo("ETF");
        search("INDIA", "RIL").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].tradingSymbol").value("RELIANCE"));
        search("INDIA", "747RIL31").andExpect(jsonPath("$.length()").value(0));
    }
    @Test void rejectsShortQueriesAndDoesNotInterpretWildcards() throws Exception {
        search("INDIA", "a").andExpect(status().isBadRequest());
        search("INDIA", "%%").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        search("INDIA", "  ").andExpect(status().isBadRequest());
    }
}
