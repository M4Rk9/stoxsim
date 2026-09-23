package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.time.Instant;
import java.time.Duration;
import java.sql.Timestamp;
import com.stoxsim.subscription.service.SubscriptionService;
import com.stoxsim.subscription.domain.SubscriptionPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.order.domain.*;
import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.order.service.OrderSettlementService;
import com.stoxsim.portfolio.domain.Holding;
import com.stoxsim.portfolio.repository.HoldingRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.subscription.provider.RazorpayTestClient;
import com.stoxsim.subscription.service.RazorpayTestBillingService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties={"stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false", "spring.task.scheduling.enabled=false",
    "stoxsim.billing.test.reconciliation-enabled=false", "stoxsim.billing.test.enabled=true", "stoxsim.billing.test.key-id=rzp_test_fixture",
    "stoxsim.billing.test.key-secret=fixture-secret", "stoxsim.billing.test.webhook-secret=01234567890123456789012345678901",
    "stoxsim.billing.test.plus-plan=plan_plus", "stoxsim.billing.test.pro-plan=plan_pro"})
class RazorpayTestBillingIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired JdbcTemplate db;
    @Autowired AppUserRepository users;
    @Autowired RazorpayTestBillingService service;
    @Autowired SubscriptionService subscriptions;
    @Autowired VirtualAccountRepository accounts;
    @Autowired TradableInstrumentRepository instruments;
    @Autowired PaperOrderRepository orders;
    @Autowired HoldingRepository holdings;
    @Autowired OrderSettlementService settlement;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext context;
    @MockitoBean RazorpayTestClient provider;
    UUID admin,other,learner;
    JsonNode remote;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
        admin=user("admin",true);other=user("other",true);learner=user("learner",false);
        when(provider.create(anyString(),any())).thenAnswer(call->{
            remote=resource(call.getArgument(1),"created");return remote;
        });
        when(provider.fetch(anyString())).thenAnswer(call->remote);
    }
    @Test void retriesAreIdempotentAndOnlyOneSubscriptionCanBeOpen() {
        UUID key=UUID.randomUUID();
        var first=service.create(admin,"PLUS",key);
        assertThat(service.create(admin,"PLUS",key).id()).isEqualTo(first.id());
        verify(provider,times(1)).create(anyString(),any());
        expectStatus(409,()->service.create(admin,"PLUS",UUID.randomUUID()));
        expectStatus(409,()->service.create(admin,"PRO",key));
        expectStatus(404,()->service.refresh(other,first.id()));
        expectStatus(403,()->service.create(learner,"PLUS",UUID.randomUUID()));
        db.update("UPDATE app_user SET platform_role='USER' WHERE id=?",admin);
        expectStatus(403,()->service.overview(admin));
    }
    @Test void timeoutReservationCanOnlyBindTheMatchingProviderResource() {
        when(provider.create(anyString(),any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Timeout"));
        UUID key=UUID.randomUUID();
        expectStatus(502,()->service.create(admin,"PLUS",key));
        var pending=service.create(admin,"PLUS",key);
        assertThat(pending.status()).isEqualTo("CREATING");
        verify(provider,times(1)).create(anyString(),any());
        remote=resource(UUID.randomUUID(),"active");
        expectStatus(502,()->service.reconcile(admin,pending.id(),"sub_fixture"));
        remote=resource(pending.id(),"active");
        assertThat(service.reconcile(admin,pending.id(),"sub_fixture").status()).isEqualTo("active");
    }
    @Test void webhooksVerifyRawBytesDeduplicateAndUseCurrentStateWithoutChangingRealPlan() throws Exception {
        db.update("INSERT INTO user_subscription(user_id,plan,subscription_status) VALUES (?,'FREE','ACTIVE')",admin);
        var subscriptionBefore=db.queryForMap("SELECT * FROM user_subscription WHERE user_id=?",admin);
        var item=service.create(admin,"PLUS",UUID.randomUUID());
        var before=db.queryForMap("SELECT * FROM app_user WHERE id=?",admin);
        byte[] raw=event(item.id());
        expectStatus(401,()->service.webhook(raw,"bad","evt_bad"));
        remote=resource(item.id(),"cancelled");
        service.webhook(raw,sign(raw),"evt_once");
        service.webhook(raw,sign(raw),"evt_once");
        service.webhook(raw,sign(raw),"evt_delayed");
        assertThat(service.overview(admin).entries().getFirst().status()).isEqualTo("cancelled");
        assertThat(db.queryForObject("SELECT count(*) FROM razorpay_test_event",Integer.class)).isEqualTo(2);
        verify(provider,times(3)).fetch("sub_fixture"); // create + two unique events
        assertThat(db.queryForMap("SELECT * FROM app_user WHERE id=?",admin)).isEqualTo(before);
        assertThat(db.queryForMap("SELECT * FROM user_subscription WHERE user_id=?",admin)).isEqualTo(subscriptionBefore);
        assertThat(db.queryForObject("SELECT count(*) FROM virtual_account WHERE user_id=?",Integer.class,admin)).isZero();
        byte[] malformed="null".getBytes(StandardCharsets.UTF_8);
        String signature=sign(malformed);
        expectStatus(400,()->service.webhook(malformed,signature,"evt_null"));
    }
    @Test void cancellationIsConfirmedWithProviderAndRepeatIsHarmless() {
        var item=service.create(admin,"PLUS",UUID.randomUUID());
        when(provider.cancel("sub_fixture")).thenAnswer(call->{remote=resource(item.id(),"cancelled");return remote;});
        assertThat(service.cancel(admin,item.id()).status()).isEqualTo("cancelled");
        service.cancel(admin,item.id());
        verify(provider,times(1)).cancel("sub_fixture");
        db.update("DELETE FROM app_user WHERE id=?",admin);
        assertThat(db.queryForObject("SELECT count(*) FROM razorpay_test_subscription",Integer.class)).isZero();
    }
    @Test void apiRequiresAuthenticationAndAdminAndRejectsOversizedUnsignedWebhooks() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/billing/test")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/billing/test").with(jwt().jwt(j->j.subject(learner.toString())))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/billing/test").with(jwt().jwt(j->j.subject(admin.toString()))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/api/v1/billing/test/webhook").content("{}")) .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/billing/test/webhook").content(new byte[65537])).andExpect(status().isPayloadTooLarge());
        db.update("UPDATE app_user SET email_verified_at=NULL WHERE id=?",admin);
        expectStatus(403,()->service.overview(admin));
    }
    @Test void benefitsRequireExplicitOptInAndProIsLimitedToFiveSeparateSandboxes() {
        when(provider.create(anyString(),any())).thenAnswer(call->{
            remote=resource(call.getArgument(1),"active").deepCopy();
            ((tools.jackson.databind.node.ObjectNode)remote).put("plan_id","plan_pro");return remote;
        });
        var item=service.create(admin,"PRO",UUID.randomUUID());
        assertThat(db.queryForObject("SELECT count(*) FROM virtual_account",Integer.class)).isZero();
        expectStatus(404,()->service.benefits(other,item.id(),true));
        expectStatus(403,()->service.benefits(learner,item.id(),true));
        assertThat(service.benefits(admin,item.id(),true).benefitStatus()).isEqualTo("ACTIVE");
        var response=subscriptions.current(admin);
        assertThat(response.billingMode()).isEqualTo("TEST");
        assertThat(response.entitlements().premiumCompetitions()).isFalse();
        assertThat(response.entitlements().multiplePortfolios()).isTrue();
        for(int i=0;i<4;i++) subscriptions.createAdditionalSandbox(admin,"test-"+i);
        subscriptions.createAdditionalSandbox(admin,"test-0");
        expectStatus(409,()->subscriptions.createAdditionalSandbox(admin,"sixth"));
        assertThat(db.queryForObject("SELECT count(*) FROM virtual_account WHERE account_kind='SANDBOX' AND leaderboard_eligible=false AND test_trading_until IS NOT NULL",Integer.class)).isEqualTo(5);
        service.benefits(admin,item.id(),false);
        assertThat(subscriptions.current(admin).plan()).isEqualTo(SubscriptionPlan.FREE);
        assertThat(db.queryForObject("SELECT count(*) FROM virtual_account WHERE active=true",Integer.class)).isZero();
        service.benefits(admin,item.id(),true);
        assertThat(db.queryForObject("SELECT count(*) FROM virtual_account",Integer.class)).isEqualTo(5);
    }
    @Test void failedRenewalHasFixedGraceAndOnlyNewPaidCycleExtendsAccess() {
        var item=service.create(admin,"PLUS",UUID.randomUUID());
        remote=resource(item.id(),"active");
        service.benefits(admin,item.id(),true);
        Instant paidThrough=Instant.now().minus(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        db.update("UPDATE razorpay_test_subscription SET paid_through=? WHERE id=?",Timestamp.from(paidThrough),item.id());
        remote=resource(item.id(),"pending");
        var grace=service.refresh(admin,item.id());
        assertThat(grace.benefitStatus()).isEqualTo("GRACE");
        assertThat(grace.accessUntil()).isEqualTo(paidThrough.plus(Duration.ofDays(3)));
        assertThat(service.refresh(admin,item.id()).accessUntil()).isEqualTo(grace.accessUntil());
        remote=resource(item.id(),"active"); // same paid_count must not buy another month
        assertThat(service.refresh(admin,item.id()).benefitStatus()).isEqualTo("EXPIRED");
        ((tools.jackson.databind.node.ObjectNode)remote).put("paid_count",2);
        assertThat(service.refresh(admin,item.id()).benefitStatus()).isEqualTo("ACTIVE");
        remote=resource(item.id(),"cancelled");
        service.reconcileBenefits(); // missed webhook recovery
        assertThat(service.overview(admin).entries().getFirst().benefitStatus()).isEqualTo("LOCKED");
        assertThat(subscriptions.current(admin).entitlements().sandboxCapitalInr()).isNull();
    }
    @Test void providerOutageExpiresLocallyAndRevokedAdminCannotKeepTestBenefits() {
        var item=service.create(admin,"PLUS",UUID.randomUUID());
        remote=resource(item.id(),"active");service.benefits(admin,item.id(),true);
        db.update("UPDATE razorpay_test_subscription SET paid_through=now()-interval '4 days',status='pending' WHERE id=?",item.id());
        when(provider.fetch(anyString())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Unavailable"));
        service.reconcileBenefits();
        assertThat(service.overview(admin).entries().getFirst().benefitStatus()).isEqualTo("EXPIRED");
        assertThat(db.queryForObject("SELECT count(*) FROM virtual_account WHERE active=true",Integer.class)).isZero();
        when(provider.fetch(anyString())).thenAnswer(call->remote);
        ((tools.jackson.databind.node.ObjectNode)remote).put("paid_count",2);
        service.refresh(admin,item.id());
        db.update("UPDATE app_user SET platform_role='USER' WHERE id=?",admin);
        assertThat(subscriptions.current(admin).sandboxAccounts()).allMatch(account->!account.active());
        assertThat(subscriptions.current(admin).entitlements().sandboxCapitalInr()).isNull();
        service.reconcileBenefits();
        assertThat(subscriptions.current(admin).plan()).isEqualTo(SubscriptionPlan.FREE);
    }
    @Test void unpaidCheckoutAndExistingPaidSubscriptionCannotEnableTestBenefits() {
        var item=service.create(admin,"PLUS",UUID.randomUUID());
        expectStatus(409,()->service.benefits(admin,item.id(),true));
        remote=resource(item.id(),"active");
        ((tools.jackson.databind.node.ObjectNode)remote).put("paid_count",0);
        expectStatus(409,()->service.benefits(admin,item.id(),true));
        remote=resource(item.id(),"active");
        db.update("INSERT INTO user_subscription(user_id,plan,subscription_status,billing_provider) VALUES (?,'PRO','ACTIVE','EXISTING')",admin);
        expectStatus(409,()->service.benefits(admin,item.id(),true));
        assertThat(subscriptions.current(admin).plan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(service.overview(admin).entries().getFirst().benefitsEnabled()).isFalse();
    }
    @Test void cancellationReleasesBuyAndSellReservationsPreservingHoldingsAndStandardAccount() {
        var item=service.create(admin,"PLUS",UUID.randomUUID());
        remote=resource(item.id(),"active");service.benefits(admin,item.id(),true);
        UUID sandbox=subscriptions.current(admin).sandboxAccounts().getFirst().id();
        var tx=new TransactionTemplate(transactionManager);
        UUID standard=tx.execute(status->accounts.save(new VirtualAccount(users.findById(admin).orElseThrow(),MarketRegion.INDIA,new BigDecimal("500000"))).getId());
        var standardBefore=db.queryForMap("SELECT * FROM virtual_account WHERE id=?",standard);
        addReservedOrders(sandbox);
        when(provider.cancel(anyString())).thenAnswer(call->{remote=resource(item.id(),"cancelled");return remote;});
        service.cancel(admin,item.id());
        service.cancel(admin,item.id());
        assertThat(db.queryForObject("SELECT blocked_cash FROM virtual_account WHERE id=?",BigDecimal.class,sandbox)).isEqualByComparingTo("0");
        assertThat(db.queryForObject("SELECT available_cash FROM virtual_account WHERE id=?",BigDecimal.class,sandbox)).isEqualByComparingTo("2500000");
        assertThat(db.queryForObject("SELECT blocked_quantity FROM holding WHERE account_id=?",Long.class,sandbox)).isZero();
        assertThat(db.queryForObject("SELECT quantity FROM holding WHERE account_id=?",Long.class,sandbox)).isEqualTo(10);
        assertThat(db.queryForList("SELECT status FROM paper_order WHERE account_id=?",String.class,sandbox)).containsExactlyInAnyOrder("CANCELLED","CANCELLED");
        assertThat(db.queryForMap("SELECT * FROM virtual_account WHERE id=?",standard)).isEqualTo(standardBefore);
    }
    @Test void settlementCannotFillExpiredSandboxEvenBeforeReconciliation() {
        var item=service.create(admin,"PLUS",UUID.randomUUID());
        remote=resource(item.id(),"active");service.benefits(admin,item.id(),true);
        UUID sandbox=subscriptions.current(admin).sandboxAccounts().getFirst().id();
        addReservedOrders(sandbox);
        db.update("UPDATE virtual_account SET test_trading_until=now()-interval '1 second' WHERE id=?",sandbox);
        db.update("UPDATE user_subscription SET test_access_until=now()-interval '1 second' WHERE user_id=?",admin);
        assertThat(subscriptions.current(admin).entitlements().sandboxCapitalInr()).isNull();
        for(var id:db.queryForList("SELECT id FROM paper_order WHERE account_id=?",UUID.class,sandbox))
            assertThat(settlement.tryFill(id,null)).isFalse(); // must reject before consulting quote/session
        assertThat(db.queryForObject("SELECT count(*) FROM trade WHERE account_id=?",Integer.class,sandbox)).isZero();
        assertThat(db.queryForObject("SELECT blocked_cash FROM virtual_account WHERE id=?",BigDecimal.class,sandbox)).isEqualByComparingTo("0");
        assertThat(db.queryForObject("SELECT blocked_quantity FROM holding WHERE account_id=?",Long.class,sandbox)).isZero();
    }
    private void addReservedOrders(UUID accountId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            var account=accounts.findByIdForUpdate(accountId).orElseThrow();
            String symbol="TEST"+UUID.randomUUID().toString().substring(0,8);
            var instrument=instruments.save(new TradableInstrument(new InstrumentSnapshot("UPSTOX",symbol,MarketRegion.INDIA,
                MarketExchange.NSE,"NSE_EQ",symbol,"Test security",null,InstrumentType.EQUITY,"INR",1,new BigDecimal("0.05"),"NORMAL"),UUID.randomUUID(),Instant.now()));
            account.reserveCash(new BigDecimal("100"));
            orders.save(new PaperOrder(account,instrument,"buy",OrderSide.BUY,OrderType.LIMIT,1,new BigDecimal("100"),new BigDecimal("100"),LocalDate.now()));
            var holding=new Holding(account,instrument,10,new BigDecimal("100"));holding.reserve(2);holdings.save(holding);
            orders.save(new PaperOrder(account,instrument,"sell",OrderSide.SELL,OrderType.LIMIT,2,new BigDecimal("100"),BigDecimal.ZERO,LocalDate.now()));
        });
    }
    private UUID user(String name,boolean admin) {
        var user=new AppUser(name+"@billing.test","hash",name);user.markEmailVerified();users.saveAndFlush(user);
        if(admin) db.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?",user.getId());
        return user.getId();
    }
    private JsonNode resource(UUID id,String state) {
        return json.readTree("{\"id\":\"sub_fixture\",\"plan_id\":\"plan_plus\",\"quantity\":1,\"status\":\""+state+
            "\",\"paid_count\":1,\"current_end\":"+Instant.now().plus(Duration.ofDays(30)).getEpochSecond()+",\"notes\":{\"stoxsim_test_reference\":\""+id+"\"}}");
    }
    private byte[] event(UUID id) {
        return ("{\"event\":\"subscription.activated\",\"payload\":{\"subscription\":{\"entity\":"+resource(id,"active")+"}}}").getBytes(StandardCharsets.UTF_8);
    }
    private String sign(byte[] raw) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(raw));
    }
    private void expectStatus(int expected,Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(expected));
    }
}
