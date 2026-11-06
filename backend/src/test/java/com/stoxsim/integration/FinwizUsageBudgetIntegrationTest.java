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
        db.execute("TRUNCATE finwiz_usage_daily,finwiz_global_usage_daily");
        user = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@budget.test", "hash", "Learner"));
        properties.setBasicDailyProviderRequests(3); properties.setExpandedDailyProviderRequests(5);
        properties.setFullDailyProviderRequests(7); properties.setGlobalDailyProviderRequests(10);
    }

    @Test void adminUsageReportsArePrivateAndExplicitAboutIncompleteCoverage() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/analytics/learning")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject(user.getId().toString()))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        db.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?", user.getId());
        budget.reserve(user.getId(), FinwizDepth.BASIC);
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
                .mapToObj(i -> (Callable<Boolean>) () -> budget.reserve(user.getId(), FinwizDepth.BASIC) != null).toList();
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
        assertThat(budget.reserve(user.getId(), FinwizDepth.FULL)).isNotNull();
        assertThat(budget.reserve(other.getId(), FinwizDepth.FULL)).isNotNull();
        assertThat(budget.reserve(user.getId(), FinwizDepth.FULL)).isNull();
        properties.setGlobalDailyProviderRequests(100);
        properties.setFullDailyProviderRequests(0);
        assertThat(budget.reserve(user.getId(), FinwizDepth.FULL)).isNull();
    }

    @Test void reportedTokensAreRecordedAgainstReservedDayWithBounds() {
        var day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        db.update("INSERT INTO finwiz_global_usage_daily(usage_day,provider_attempts) VALUES(?,1)", java.sql.Date.valueOf(day));
        budget.recordReportedTokens(day,123);
        budget.recordReportedTokens(day,-10);
        budget.recordReportedTokens(day,1000001);
        assertThat(db.queryForObject("SELECT reported_total_tokens FROM finwiz_global_usage_daily WHERE usage_day=?", Long.class, java.sql.Date.valueOf(day))).isEqualTo(123);
        assertThat(db.queryForObject("SELECT responses_with_usage FROM finwiz_global_usage_daily WHERE usage_day=?", Integer.class, java.sql.Date.valueOf(day))).isEqualTo(1);
    }

    @Test void deletingAccountRemovesPrivateUsageAndPreservesAnonymousGlobalBudget() {
        budget.reserve(user.getId(), FinwizDepth.BASIC);
        users.deleteById(user.getId());
        assertThat(db.queryForObject("SELECT count(*) FROM finwiz_usage_daily WHERE user_id=?", Integer.class, user.getId())).isZero();
        assertThat(db.queryForObject("SELECT sum(provider_attempts) FROM finwiz_global_usage_daily", Integer.class)).isEqualTo(1);
    }

    @Test void expiredCountersArePurgedWithoutRemovingCurrentUsage() {
        var old = java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC).minusDays(180));
        db.update("INSERT INTO finwiz_usage_daily(user_id,usage_day,provider_attempts) VALUES(?,?,1)", user.getId(), old);
        db.update("INSERT INTO finwiz_global_usage_daily(usage_day,provider_attempts) VALUES(?,1)", old);
        budget.reserve(user.getId(), FinwizDepth.BASIC);
        budget.purgeExpired();
        assertThat(db.queryForObject("SELECT count(*) FROM finwiz_usage_daily", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM finwiz_global_usage_daily", Integer.class)).isEqualTo(1);
    }
}
