package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.finwiz.config.FinwizProperties;
import com.stoxsim.finwiz.service.FinwizDepth;
import com.stoxsim.finwiz.service.FinwizUsageBudget;

@Testcontainers
@SpringBootTest(properties = {"stoxsim.finwiz.enabled=false", "stoxsim.security.rate-limit.enabled=false",
    "stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false", "stoxsim.market-data.alpaca.polling-enabled=false"})
class FinwizUsageBudgetIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired AppUserRepository users;
    @Autowired FinwizUsageBudget budget;
    @Autowired FinwizProperties properties;
    @Autowired JdbcTemplate db;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    AppUser user;
    @BeforeEach void setup() {
        db.execute("TRUNCATE finwiz_usage_daily,finwiz_global_usage_daily,finwiz_provider_usage_daily,finwiz_request_reservation,finwiz_provider_admission");
        user = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@budget.test", "hash", "Learner"));
        properties.setBasicDailyProviderRequests(3); properties.setExpandedDailyProviderRequests(5);
        properties.setFullDailyProviderRequests(7); properties.setGlobalDailyProviderRequests(10);
    }

    private FinwizUsageBudget.Reservation complete(UUID id, FinwizDepth depth) {
        var request=budget.reserveRequest(id,depth);
        if(request==null) return null;
        var attempt=budget.reserveAttempt(request,depth,"GEMINI",500,100,100000,1000000,100);
        budget.finishRequest(request,attempt!=null);
        return attempt;
    }
    @Test void failoverCountsTwoAttemptsButOnlyOneSuccessfulAnswer() {
        var request=budget.reserveRequest(user.getId(),FinwizDepth.BASIC);
        assertThat(request).isNotNull();
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",1,100,100000,1000000,100)).isNotNull();
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",1,100,100000,1000000,100)).isNull();
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GEMINI",5,100,100000,1000000,100)).isNotNull();
        assertThat(budget.finishRequest(request,true)).isTrue();
        assertThat(budget.finishRequest(request,true)).isFalse();
        assertThat(db.queryForObject("SELECT successful_requests FROM finwiz_usage_daily WHERE user_id=?",Integer.class,user.getId())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT provider_attempts FROM finwiz_usage_daily WHERE user_id=?",Integer.class,user.getId())).isEqualTo(2);
        var failed=budget.reserveRequest(user.getId(),FinwizDepth.BASIC);
        assertThat(budget.reserveAttempt(failed,FinwizDepth.BASIC,"GEMINI",5,100,100000,1000000,100)).isNotNull();
        assertThat(budget.finishRequest(failed,false)).isTrue();
        assertThat(db.queryForObject("SELECT successful_requests FROM finwiz_usage_daily WHERE user_id=?",Integer.class,user.getId())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT sum(provider_attempts) FROM finwiz_global_usage_daily",Integer.class)).isEqualTo(3);
    }
    @Test void rollingProviderRateAndTokenBudgetRejectWithoutConsumingAttempts() {
        var request=budget.reserveRequest(user.getId(),FinwizDepth.BASIC);
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",10,2,500,1000,300)).isNotNull();
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",10,2,500,1000,201)).isNull();
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",10,2,500,1000,200)).isNotNull();
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",10,2,500,1000,1)).isNull();
        db.update("UPDATE finwiz_provider_admission SET admitted_at=now()-interval '61 seconds'");
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",10,2,1000,1000,501)).isNull();
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",10,2,1000,1000,500)).isNotNull();
        assertThat(budget.reserveAttempt(request,FinwizDepth.BASIC,"GROQ",10,2,1000,1000,1)).isNull();
        assertThat(db.queryForObject("SELECT provider_attempts FROM finwiz_provider_usage_daily WHERE provider='GROQ'",Integer.class)).isEqualTo(3);
        assertThat(budget.finishRequest(request,true)).isTrue();
    }
    @Test void duplicateClientKeyIsRejectedDuringAndAfterSettlementButScopedToUser() {
        UUID key=UUID.randomUUID();
        var first=budget.reserveRequest(user.getId(),FinwizDepth.BASIC,key);
        assertThat(first).isNotNull();
        assertThatThrownBy(()->budget.reserveRequest(user.getId(),FinwizDepth.BASIC,key))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("409");
        assertThat(budget.finishRequest(first,false)).isTrue();
        assertThatThrownBy(()->budget.reserveRequest(user.getId(),FinwizDepth.BASIC,key))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("409");
        var other=users.saveAndFlush(new AppUser(UUID.randomUUID()+"@budget.test","hash","Other"));
        assertThat(budget.reserveRequest(other.getId(),FinwizDepth.BASIC,key)).isNotNull();
        assertThat(budget.reserveRequest(user.getId(),FinwizDepth.BASIC,UUID.randomUUID())).isNotNull();
    }
    @Test void concurrentDuplicateClientKeysReserveOnlyOnce() throws Exception {
        UUID key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(6)) {
            List<Callable<Boolean>> tasks=IntStream.range(0,12).mapToObj(i->(Callable<Boolean>)()->{
                try { return budget.reserveRequest(user.getId(),FinwizDepth.FULL,key)!=null; }
                catch(org.springframework.web.server.ResponseStatusException e) { return false; }
            }).toList();
            int accepted=0; for(var future:pool.invokeAll(tasks)) if(future.get()) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
    }
    @Test void adminUsageReportsArePrivateAndExplicitAboutIncompleteCoverage() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/analytics/learning")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject(user.getId().toString()))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        db.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?", user.getId());
        complete(user.getId(), FinwizDepth.BASIC);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/analytics/learning")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject(user.getId().toString()))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.providerAttempts").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fullLearningWindowObserved").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fullAiWindowObserved").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.costStatus").value(org.hamcrest.Matchers.containsString("invoices must be verified")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/analytics/learning?from=invalid")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject(user.getId().toString()))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test void concurrentCallsCannotExceedSharedUserBudget() throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Boolean>> tasks = IntStream.range(0,20)
                .mapToObj(i -> (Callable<Boolean>) () -> complete(user.getId(), FinwizDepth.BASIC) != null).toList();
            int accepted = 0;
            for (var future : executor.invokeAll(tasks)) if (future.get()) accepted++;
            assertThat(accepted).isEqualTo(3);
        }
        assertThat(db.queryForObject("SELECT provider_attempts FROM finwiz_usage_daily WHERE user_id=?", Integer.class, user.getId())).isEqualTo(3);
        assertThat(db.queryForObject("SELECT sum(provider_attempts) FROM finwiz_global_usage_daily", Integer.class)).isEqualTo(3);
    }

    @Test void globalBudgetBoundsMultipleAccountsAndZeroDisablesProvider() {
        properties.setGlobalDailyProviderRequests(2);
        var other = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@budget.test", "hash", "Other"));
        assertThat(complete(user.getId(), FinwizDepth.FULL)).isNotNull();
        assertThat(complete(other.getId(), FinwizDepth.FULL)).isNotNull();
        assertThat(complete(user.getId(), FinwizDepth.FULL)).isNull();
        properties.setGlobalDailyProviderRequests(100);
        properties.setFullDailyProviderRequests(0);
        assertThat(complete(user.getId(), FinwizDepth.FULL)).isNull();
    }

    @Test void reportedTokensAreRecordedAgainstReservedDayWithBounds() {
        var day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        db.update("INSERT INTO finwiz_global_usage_daily(usage_day,provider_attempts) VALUES(?,1)", java.sql.Date.valueOf(day));
        budget.recordReportedTokens(day,"GEMINI",123);
        budget.recordReportedTokens(day,"GEMINI",-10);
        budget.recordReportedTokens(day,"GEMINI",1000001);
        assertThat(db.queryForObject("SELECT reported_total_tokens FROM finwiz_global_usage_daily WHERE usage_day=?", Long.class, java.sql.Date.valueOf(day))).isEqualTo(123);
        assertThat(db.queryForObject("SELECT responses_with_usage FROM finwiz_global_usage_daily WHERE usage_day=?", Integer.class, java.sql.Date.valueOf(day))).isEqualTo(1);
    }

    @Test void deletingAccountRemovesPrivateUsageAndPreservesAnonymousGlobalBudget() {
        complete(user.getId(), FinwizDepth.BASIC);
        users.deleteById(user.getId());
        assertThat(db.queryForObject("SELECT count(*) FROM finwiz_usage_daily WHERE user_id=?", Integer.class, user.getId())).isZero();
        assertThat(db.queryForObject("SELECT sum(provider_attempts) FROM finwiz_global_usage_daily", Integer.class)).isEqualTo(1);
    }

    @Test void expiredCountersArePurgedWithoutRemovingCurrentUsage() {
        var old = java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC).minusDays(180));
        db.update("INSERT INTO finwiz_usage_daily(user_id,usage_day,provider_attempts) VALUES(?,?,1)", user.getId(), old);
        db.update("INSERT INTO finwiz_global_usage_daily(usage_day,provider_attempts) VALUES(?,1)", old);
        complete(user.getId(), FinwizDepth.BASIC);
        budget.purgeExpired();
        assertThat(db.queryForObject("SELECT count(*) FROM finwiz_usage_daily", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM finwiz_global_usage_daily", Integer.class)).isEqualTo(1);
    }
}
