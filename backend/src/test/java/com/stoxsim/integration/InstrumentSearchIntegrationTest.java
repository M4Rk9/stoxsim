package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import com.stoxsim.instrument.domain.*;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.domain.MarketRegion;

@Testcontainers
@Transactional
@SpringBootTest(properties={"stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false"})
class InstrumentSearchIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Container @ServiceConnection(name="redis") static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Autowired TradableInstrumentRepository instruments;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private TradableInstrument add(String symbol, String name, MarketExchange exchange, InstrumentType type, String isin) {
        boolean us = exchange == MarketExchange.NYSE_ARCA;
        return instruments.saveAndFlush(new TradableInstrument(new InstrumentSnapshot(us ? "ALPACA" : "UPSTOX", UUID.randomUUID().toString(),
            us ? MarketRegion.UNITED_STATES : MarketRegion.INDIA, exchange, us ? "US_EQUITY" : type == InstrumentType.INDEX ? "NSE_INDEX" : "NSE_EQ",
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
