package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.api.IndexQuoteResponse;
import com.stoxsim.market.service.IndexQuoteService;
import com.stoxsim.portfolio.api.*;
import com.stoxsim.portfolio.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@Import(PortfolioHistoryIntegrationTest.TimeConfig.class)
@SpringBootTest(properties={"stoxsim.market-data.upstox.stream-enabled=false","stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false","stoxsim.portfolio.history.enabled=false","stoxsim.portfolio.history.benchmark-retention-enabled=true"})
class PortfolioHistoryIntegrationTest {
    static final Instant NOW=Instant.parse("2026-10-30T23:40:00Z");
    @TestConfiguration static class TimeConfig { @Bean @Primary Clock historyClock() { return Clock.fixed(NOW,ZoneOffset.UTC); } }
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired JdbcTemplate db;
    @Autowired AppUserRepository users;
    @Autowired VirtualAccountRepository accounts;
    @Autowired PortfolioHistoryService service;
    @Autowired PortfolioHistoryCollector collector;
    @Autowired IndiaMarketSessionService calendar;
    @Autowired WebApplicationContext context;
    @MockitoBean PortfolioValuationService valuation;
    @MockitoBean IndexQuoteService indexes;
    UUID user,account;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
        var owner=users.saveAndFlush(new AppUser("history@test.local","hash","History"));user=owner.getId();
        account=accounts.saveAndFlush(new VirtualAccount(owner,MarketRegion.INDIA,new BigDecimal("500000"))).getId();
        db.update("INSERT INTO user_subscription(user_id,plan,subscription_status) VALUES (?,'PRO','ACTIVE')",user);
        when(indexes.current()).thenReturn(List.of(new IndexQuoteResponse("NIFTY_50","NIFTY 50","NSE","Nifty",new BigDecimal("25000"),null,null,null,MarketDataStatus.CLOSED,Instant.parse("2026-10-30T10:00:00Z"))));
        when(valuation.valueForAccount(user,account)).thenReturn(new PortfolioResponse(MarketRegion.INDIA,"INR",new BigDecimal("500000"),new BigDecimal("500000"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("500000"),BigDecimal.ZERO,PortfolioPositionResponse.PricingStatus.CLOSED,NOW,List.of()));
    }
    @Test void nightlyCaptureIsIdempotentAndDeletionCascades() {
        collector.capture(account);collector.capture(account);
        var history=service.history(user,account,30);
        assertThat(history.points()).hasSize(1);
        assertThat(history.points().getFirst().equity()).isEqualByComparingTo("500000");
        assertThat(history.returnPercent()).isNull();
        verify(valuation,times(1)).valueForAccount(user,account);
        db.update("DELETE FROM app_user WHERE id=?",user);
        assertThat(db.queryForObject("SELECT count(*) FROM portfolio_history",Integer.class)).isZero();
    }
    @Test void alignedHistoryComputesReturnsAndRiskWithoutMixingAccounts() throws Exception {
        int i=0;
        for(var day=LocalDate.of(2026,9,21);!day.isAfter(LocalDate.of(2026,10,30));day=day.plusDays(1)) {
            if(!calendar.isTradingDay(MarketExchange.NSE,day)) continue;
            add(day,new BigDecimal("100").add(BigDecimal.valueOf(i)),new BigDecimal("200").add(BigDecimal.valueOf(i++)),new BigDecimal("100"));
        }
        var result=service.history(user,account,90);
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.returnPercent()).isCloseTo((double)i-1,within(1e-8));
        assertThat(result.maximumDrawdownPercent()).isZero();
        assertThat(result.risk().volatilityPercent()).isNotNull();
        assertThat(result.risk().beta()).isNotNull();
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/accounts/"+account+"/portfolio/history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/"+account+"/portfolio/history").with(jwt().jwt(j->j.subject(UUID.randomUUID().toString())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/"+account+"/portfolio/history").with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void cashAdjustmentsAndMissingSessionsSuppressMisleadingPerformance() {
        add(LocalDate.of(2026,10,26),new BigDecimal("100"),new BigDecimal("200"),new BigDecimal("100"));
        add(LocalDate.of(2026,10,28),new BigDecimal("1100"),new BigDecimal("202"),new BigDecimal("1100"));
        var result=service.history(user,account,30);
        assertThat(result.cashFlowBreaks()).isEqualTo(1);assertThat(result.missingSessions()).isEqualTo(1);
        assertThat(result.returnPercent()).isNull();assertThat(result.risk().volatilityPercent()).isNull();
        assertThat(result.points()).allMatch(p->p.portfolioIndex()==null && p.benchmarkIndex()==null);
    }
    @Test void tradeCashIsNotAnExternalFlowAndDrawdownIsPeakBased() {
        add(LocalDate.of(2026,10,26),new BigDecimal("100"),new BigDecimal("200"),new BigDecimal("100"));
        add(LocalDate.of(2026,10,27),new BigDecimal("80"),null,new BigDecimal("50"));
        db.update("UPDATE portfolio_history SET trade_cash=-50 WHERE observed_day='2026-10-27'");
        var result=service.history(user,account,30);
        assertThat(result.cashFlowBreaks()).isZero();assertThat(result.returnPercent()).isCloseTo(-20.0,within(1e-10));
        assertThat(result.maximumDrawdownPercent()).isCloseTo(20.0,within(1e-10));
        assertThat(result.benchmarkReturnPercent()).isNull();
    }
    @Test void freeAndExpiredPlansCannotReceivePremiumMetrics() {
        add(LocalDate.of(2026,10,26),new BigDecimal("100"),new BigDecimal("200"),new BigDecimal("100"));
        add(LocalDate.of(2026,10,27),new BigDecimal("101"),new BigDecimal("202"),new BigDecimal("100"));
        db.update("UPDATE user_subscription SET subscription_status='PAST_DUE' WHERE user_id=?",user);
        var result=service.history(user,account,30);
        assertThat(result.tier()).isEqualTo("FREE");assertThat(result.returnPercent()).isNull();
        assertThat(result.points()).allMatch(p->p.portfolioIndex()==null && p.benchmarkIndex()==null);
        assertThatThrownBy(()->service.history(user,account,90)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(403));
        assertThatThrownBy(()->service.history(user,account,999)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));
    }
    @Test void stalePricesAreRecordedAsGapsInsteadOfCostBasisPerformance() {
        var position=mock(PortfolioPositionResponse.class);
        when(position.pricingStatus()).thenReturn(PortfolioPositionResponse.PricingStatus.STALE);
        var base=valuation.valueForAccount(user,account);
        when(valuation.valueForAccount(user,account)).thenReturn(new PortfolioResponse(base.marketRegion(),base.currency(),base.startingCapital(),base.availableCash(),base.blockedCash(),base.investedValue(),base.marketValue(),base.realizedProfitLoss(),base.unrealizedProfitLoss(),base.totalProfitLoss(),base.totalAccountValue(),base.totalReturnPercent(),base.dataStatus(),NOW,List.of(position)));
        collector.capture(account);
        var result=service.history(user,account,30);
        assertThat(result.points().getFirst().quality()).isEqualTo("MISSING_PRICES");
        assertThat(result.points().getFirst().equity()).isNull();
        assertThat(result.returnPercent()).isNull();
    }
    private void add(LocalDate day,BigDecimal equity,BigDecimal benchmark,BigDecimal cash) {
        db.update("INSERT INTO portfolio_history(account_id,observed_day,observed_at,currency,equity,cash,trade_cash,capital,quality,benchmark) VALUES (?,?,?,'INR',?,?,0,100,'OBSERVED',?)",account,day,java.sql.Timestamp.from(day.atTime(23,40).toInstant(ZoneOffset.UTC)),equity,cash,benchmark);
    }
}
