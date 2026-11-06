package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.notification.*;
import com.stoxsim.notification.NotificationDtos.*;
import io.micrometer.core.instrument.MeterRegistry;

@Testcontainers
@SpringBootTest(properties={"stoxsim.notifications.scheduler-enabled=false","stoxsim.notifications.delivery-enabled=false",
 "stoxsim.market-data.upstox.stream-enabled=false","stoxsim.market-data.upstox.instrument-sync-on-startup=false",
 "stoxsim.market-data.alpaca.instrument-sync-on-startup=false","stoxsim.market-data.alpaca.polling-enabled=false",
 "stoxsim.security.rate-limit.enabled=false","stoxsim.portfolio.history.enabled=false"})
class NotificationIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
 @Autowired NotificationService service;
 @Autowired NotificationTransactions transactions;
 @Autowired NotificationOutbox configuredOutbox;
 @Autowired AppUserRepository users;
 @Autowired JdbcTemplate db;
 @Autowired Clock clock;
 @Autowired PlatformTransactionManager manager;
 @Autowired MeterRegistry meters;
 @Autowired WebApplicationContext context;
 MockMvc mvc;AppUser a,b;
 @BeforeEach void setup(){
  db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
  a=new AppUser("a-"+UUID.randomUUID()+"@stoxsim.test","hash","Learner A");a.markEmailVerified();users.saveAndFlush(a);
  b=users.saveAndFlush(new AppUser("b-"+UUID.randomUUID()+"@stoxsim.test","hash","Learner B"));
  mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
 }
 Preferences optedIn(){return new Preferences(true,true,true,false,false,true,true);}
 UUID publish(String key){return service.publish(new NotificationRequested(a.getId(),NotificationCategory.LEARNING,key,"Achievement","A learning achievement is ready.","/progress"));}
 @Test void inboxOwnershipReadAndDeduplicationAreAuthoritative() throws Exception {
  UUID id=publish("achievement:1");assertThat(publish("achievement:1")).isNull();assertThat(service.inbox(a.getId()).unreadCount()).isEqualTo(1);assertThat(service.inbox(b.getId()).items()).isEmpty();
  mvc.perform(post("/api/v1/notifications/"+id+"/read").with(jwt().jwt(t->t.subject(b.getId().toString())))).andExpect(status().isNotFound());
  service.markRead(a.getId(),id);service.markRead(a.getId(),id);assertThat(service.inbox(a.getId()).unreadCount()).isZero();
  mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
 }
 @Test void preferencesRequireVerifiedEmailAndDoNotReplayPreviouslyOptedOutEvents() throws Exception {
  assertThat(service.preferences(a.getId())).isEqualTo(Preferences.defaults());
  assertThatThrownBy(()->service.updatePreferences(b.getId(),optedIn())).isInstanceOf(ResponseStatusException.class);
  mvc.perform(put("/api/v1/notifications/preferences").with(jwt().jwt(t->t.subject(a.getId().toString()))).contentType(MediaType.APPLICATION_JSON).content("{\"learning\":true}")).andExpect(status().isBadRequest());
  service.updatePreferences(a.getId(),new Preferences(false,true,true,false,false,false,false));assertThat(publish("hidden")).isNull();
  service.updatePreferences(a.getId(),Preferences.defaults());assertThat(publish("hidden")).isNull();
 }
 @Test void dailyCapsBoundOptionalActivityWithoutSuppressingSecurity(){
  for(int i=0;i<20;i++)publish("a:"+i);assertThat(service.inbox(a.getId()).items()).hasSize(10);
  assertThat(service.publish(new NotificationRequested(a.getId(),NotificationCategory.SECURITY,"security:1","Security changed","Review your account.","/settings"))).isNotNull();
  assertThat(db.queryForObject("SELECT count(*) FROM notification_outbox",Long.class)).isZero();
 }
 @Test void accountSwitchingMovesInstallationAndPreviousOwnerCannotDeleteIt(){
  service.updatePreferences(a.getId(),optedIn());service.updatePreferences(b.getId(),new Preferences(true,true,true,false,false,true,false));
  service.registerDevice(a.getId(),new Device("installation-token","ANDROID"));service.registerDevice(b.getId(),new Device("installation-token","ANDROID"));service.removeDevice(a.getId(),"installation-token");
  assertThat(db.queryForObject("SELECT user_id FROM notification_device",UUID.class)).isEqualTo(b.getId());service.removeAllDevices(b.getId());assertThat(db.queryForObject("SELECT count(*) FROM notification_device",Long.class)).isZero();
 }
 @Test void devicesAreBoundedAndDisablingPushRemovesRegistrations(){
  service.updatePreferences(a.getId(),optedIn());for(int i=0;i<9;i++)service.registerDevice(a.getId(),new Device("installation-"+i,"ANDROID"));
  assertThat(db.queryForObject("SELECT count(*) FROM notification_device",Long.class)).isEqualTo(5);service.updatePreferences(a.getId(),Preferences.defaults());assertThat(db.queryForObject("SELECT count(*) FROM notification_device",Long.class)).isZero();
 }
 @Test void unsubscribeIsScopedIdempotentAndComplaintsCannotBeClearedByReoptin() throws Exception {
  service.updatePreferences(a.getId(),optedIn());publish("before-unsubscribe");String token=service.unsubscribeToken(a.getId());
  service.unsubscribe("made-up-token-xxxxxxxxxxxxxxxxxxxxxxxxxxxx");assertThat(service.preferences(a.getId()).emailDigest()).isTrue();
  mvc.perform(post("/api/v1/notifications/unsubscribe").param("token",token)).andExpect(status().isNoContent());
  service.unsubscribe(token);assertThat(service.preferences(a.getId()).emailDigest()).isFalse();
  assertThat(db.queryForObject("SELECT count(*) FROM notification_outbox WHERE channel='EMAIL' AND status='PENDING'",Long.class)).isZero();
  service.suppress(a.getId(),"COMPLAINT");service.updatePreferences(a.getId(),optedIn());assertThat(db.queryForObject("SELECT reason FROM notification_suppression WHERE user_id=?",String.class,a.getId())).isEqualTo("COMPLAINT");
  assertThatThrownBy(()->service.administratorSuppress(b.getId(),a.getId(),"BOUNCE")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void deliveryIsDisabledByDefaultAndSendingIsDeduplicatedAndCapped(){
  service.updatePreferences(a.getId(),optedIn());publish("first");publish("second");configuredOutbox.dispatch();
  assertThat(db.queryForObject("SELECT count(*) FROM notification_outbox WHERE status='PENDING'",Long.class)).isEqualTo(4);
  AtomicInteger sent=new AtomicInteger();var outbox=worker(sent,NotificationTransport.Result.ACCEPTED);outbox.dispatch();outbox.dispatch();assertThat(sent.get()).isEqualTo(1);
  assertThat(db.queryForObject("SELECT count(*) FROM notification_outbox WHERE status='SENT'",Long.class)).isEqualTo(1);
 }
 @Test void unknownOutcomeAndCrashedWorkerAreNeverAutomaticallyReplayed(){
  service.updatePreferences(a.getId(),optedIn());publish("ambiguous");AtomicInteger calls=new AtomicInteger();var outbox=worker(calls,NotificationTransport.Result.UNKNOWN);
  outbox.dispatch();outbox.dispatch();assertThat(calls.get()).isEqualTo(1);assertThat(db.queryForObject("SELECT count(*) FROM notification_outbox WHERE status='UNCERTAIN'",Long.class)).isEqualTo(1);
  db.update("UPDATE notification_outbox SET status='PROCESSING',claimed_at=now()-interval '10 minutes' WHERE channel='PUSH'");outbox.dispatch();assertThat(db.queryForObject("SELECT count(*) FROM notification_outbox WHERE status='UNCERTAIN'",Long.class)).isEqualTo(2);
 }
 @Test void definiteRejectionsHaveBoundedRetries(){
  service.updatePreferences(a.getId(),optedIn());publish("retry");AtomicInteger calls=new AtomicInteger();var outbox=worker(calls,NotificationTransport.Result.RETRYABLE_REJECTION);
  for(int i=0;i<4;i++){outbox.dispatch();db.update("UPDATE notification_outbox SET available_at=now()-interval '1 minute' WHERE status='PENDING'");}
  assertThat(calls.get()).isEqualTo(3);assertThat(db.queryForObject("SELECT attempts FROM notification_outbox WHERE channel='EMAIL'",Integer.class)).isEqualTo(3);
  assertThat(db.queryForObject("SELECT status FROM notification_outbox WHERE channel='EMAIL'",String.class)).isEqualTo("FAILED");
 }
 @Test void promotionalConsentIsRequiredAtCreationAndRecheckedAtDelivery(){
  service.updatePreferences(a.getId(),new Preferences(true,true,true,true,false,false,true));
  assertThat(service.publish(new NotificationRequested(a.getId(),NotificationCategory.PRODUCT,"promo:blocked","Optional offer","Offer","/settings",true))).isNull();
  service.updatePreferences(a.getId(),new Preferences(true,true,true,true,true,false,true));
  assertThat(service.publish(new NotificationRequested(a.getId(),NotificationCategory.PRODUCT,"promo:queued","Optional offer","Offer","/settings",true))).isNotNull();
  service.updatePreferences(a.getId(),new Preferences(true,true,true,true,false,false,true));
  AtomicInteger calls=new AtomicInteger();worker(calls,NotificationTransport.Result.ACCEPTED).dispatch();assertThat(calls.get()).isZero();
 }
 @Test void pollingDoesNotInventHistoricalActivityAndDeduplicatesAchievements(){
  var scanner=new NotificationScheduler(db,transactions,configuredOutbox,clock);
  db.update("INSERT INTO achievement_unlock(user_id,achievement_code,unlocked_at) VALUES(?,'BEFORE',now()-interval '1 day')",a.getId());
  db.update("INSERT INTO achievement_unlock(user_id,achievement_code,unlocked_at) VALUES(?,'AFTER',now())",a.getId());scanner.materialize();scanner.materialize();
  assertThat(service.inbox(a.getId()).items()).hasSize(1);assertThat(service.inbox(a.getId()).items().getFirst().category()).isEqualTo(NotificationCategory.LEARNING);
 }
 @Test void deletingAccountCascadesPrivateNotificationData(){
  service.updatePreferences(a.getId(),optedIn());publish("delete");service.unsubscribeToken(a.getId());service.registerDevice(a.getId(),new Device("private-token","ANDROID"));users.deleteById(a.getId());users.flush();
  for(String table:List.of("notification_settings","user_notification","notification_device","notification_outbox","notification_unsubscribe","notification_source_seen"))assertThat(db.queryForObject("SELECT count(*) FROM "+table,Long.class)).isZero();
 }
 NotificationOutbox worker(AtomicInteger calls,NotificationTransport.Result result){
  NotificationTransport transport=new NotificationTransport(){public String channel(){return "EMAIL";}public boolean configured(){return true;}public Result deliver(Message m){calls.incrementAndGet();return result;}};
  return new NotificationOutbox(db,service,clock,List.of(transport),manager,true,meters);
 }
}
