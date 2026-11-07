package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.stoxsim.billing.LiveBillingService;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountLifecycleService;

@Testcontainers
@Transactional
@SpringBootTest(properties={"stoxsim.finwiz.enabled=false", "stoxsim.market-data.upstox.stream-enabled=false",
    "stoxsim.market-data.upstox.instrument-sync-on-startup=false", "stoxsim.market-data.alpaca.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.polling-enabled=false"})
class CommercialAccountLifecycleIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired AppUserRepository users;
    @Autowired AccountLifecycleService lifecycle;
    @Autowired JdbcTemplate db;
    @MockitoBean LiveBillingService billing;

    private AppUser user() { return users.saveAndFlush(new AppUser(UUID.randomUUID()+"@lifecycle.test","hash","Learner")); }
    private void device(UUID user, String token) {
        db.update("INSERT INTO notification_device(token_hash,user_id,token,platform) VALUES (?,?,?,'ANDROID')",
            UUID.randomUUID().toString(),user,token);
    }
    @ParameterizedTest
    @ValueSource(strings={"SIGNED_OUT", "SESSION_REVOKED", "ALL_SESSIONS_REVOKED"})
    void sessionRevocationRemovesOnlyOwnedNotificationDevices(String event) {
        var user=user(); var other=user();
        device(user.getId(),"owned-device"); device(other.getId(),"other-device");
        if (event.equals("ALL_SESSIONS_REVOKED")) lifecycle.logoutAll(user.getId());
        else lifecycle.audit(user.getId(),event,null);
        assertThat(db.queryForList("SELECT token FROM notification_device",String.class)).containsExactly("other-device");
    }
    private UUID purchase(UUID user) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO billing_purchase(id,user_id,provider,plan,request_key,provider_product,encrypted_token,token_hash) VALUES (?,?,'GOOGLE_PLAY','PLUS',?,'plus','private-encrypted-token',?)",
            id,user,UUID.randomUUID(),UUID.randomUUID().toString());
        return id;
    }
    @Test void accessRevocationDoesNotProveThatRemoteRenewalStopped() {
        var user=user(); var purchase=purchase(user.getId());
        db.update("UPDATE billing_purchase SET revoked=true,status='REVOKED' WHERE id=?",purchase);
        assertThatThrownBy(()->lifecycle.deleteVerifiedAccount(user.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(users.existsById(user.getId())).isTrue();
    }
    @Test void confirmedCancellationAllowsDeletionAndCascadesPrivateRecords() {
        var user=user(); var purchase=purchase(user.getId());
        db.update("UPDATE billing_purchase SET cancellation_status='CONFIRMED',renewal_stopped=true WHERE id=?",purchase);
        db.update("INSERT INTO decision_reflection(id,user_id,reason,created_at,updated_at) VALUES (?,?,'Private reflection',now(),now())",UUID.randomUUID(),user.getId());
        lifecycle.deleteVerifiedAccount(user.getId());
        assertThat(users.existsById(user.getId())).isFalse();
        assertThat(db.queryForObject("SELECT count(*) FROM billing_purchase WHERE user_id=?",Integer.class,user.getId())).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM decision_reflection WHERE user_id=?",Integer.class,user.getId())).isZero();
    }
    @Test void deletionRechecksRestartableCancellationAndFailsClosedDuringProviderOutage() {
        var user=user(); var purchase=purchase(user.getId());
        db.update("UPDATE billing_purchase SET cancellation_status='CONFIRMED',renewal_stopped=true,paid_through=now()+interval '1 day' WHERE id=?",purchase);
        when(billing.refresh(user.getId(),purchase)).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Provider unavailable"));
        assertThatThrownBy(()->lifecycle.deleteVerifiedAccount(user.getId())).isInstanceOf(ResponseStatusException.class);
        verify(billing).refresh(user.getId(),purchase);
        assertThat(users.existsById(user.getId())).isTrue();
    }
    @Test void exportContainsOwnedReflectionsAndBillingButNeverTransportCredentials() {
        var user=user(); var other=user(); purchase(user.getId());
        device(user.getId(),"private-push-token"); device(other.getId(),"other-push-token");
        db.update("INSERT INTO notification_settings(user_id,email_digest) VALUES (?,true)",user.getId());
        db.update("INSERT INTO user_notification(id,user_id,event_key,category,title,body,href) VALUES (?,?,'owned','LEARNING','Lesson ready','Owned reminder','/learn')",UUID.randomUUID(),user.getId());
        db.update("INSERT INTO user_notification(id,user_id,event_key,category,title,body,href) VALUES (?,?,'other','LEARNING','Private title','Other reminder','/learn')",UUID.randomUUID(),other.getId());
        db.update("INSERT INTO decision_reflection(id,user_id,reason,created_at,updated_at) VALUES (?,?,'My reflection',now(),now())",UUID.randomUUID(),user.getId());
        db.update("INSERT INTO decision_reflection(id,user_id,reason,created_at,updated_at) VALUES (?,?,'Other private note',now(),now())",UUID.randomUUID(),other.getId());
        var exported=lifecycle.exportAccount(user.getId());
        assertThat((List<?>)exported.get("decisionReflections")).hasSize(1);
        assertThat((List<?>)exported.get("billingPurchases")).hasSize(1);
        assertThat((List<?>)exported.get("notificationDevices")).hasSize(1);
        assertThat((List<?>)exported.get("notificationPreferences")).hasSize(1);
        assertThat((List<?>)exported.get("notifications")).hasSize(1);
        assertThat(exported.toString()).contains("My reflection","Owned reminder").doesNotContain("Other private note","private-encrypted-token","token_hash","encrypted_token","private-push-token","other-push-token","Other reminder");
        assertThat((List<?>)lifecycle.exportAccount(other.getId()).get("billingPurchases")).isEmpty();
    }
}
