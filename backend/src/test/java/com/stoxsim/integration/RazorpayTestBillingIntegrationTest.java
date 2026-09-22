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
    "stoxsim.billing.test.enabled=true", "stoxsim.billing.test.key-id=rzp_test_fixture",
    "stoxsim.billing.test.key-secret=fixture-secret", "stoxsim.billing.test.webhook-secret=01234567890123456789012345678901",
    "stoxsim.billing.test.plus-plan=plan_plus", "stoxsim.billing.test.pro-plan=plan_pro"})
class RazorpayTestBillingIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired JdbcTemplate db;
    @Autowired AppUserRepository users;
    @Autowired RazorpayTestBillingService service;
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
    private UUID user(String name,boolean admin) {
        var user=new AppUser(name+"@billing.test","hash",name);user.markEmailVerified();users.saveAndFlush(user);
        if(admin) db.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?",user.getId());
        return user.getId();
    }
    private JsonNode resource(UUID id,String state) {
        return json.readTree("{\"id\":\"sub_fixture\",\"plan_id\":\"plan_plus\",\"quantity\":1,\"status\":\""+state+
            "\",\"paid_count\":1,\"current_end\":1800000000,\"notes\":{\"stoxsim_test_reference\":\""+id+"\"}}");
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
