package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.stoxsim.billing.*;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.subscription.service.SubscriptionService;
import com.stoxsim.subscription.domain.SubscriptionPlan;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;
import com.stoxsim.account.repository.VirtualAccountRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Testcontainers
@SpringBootTest(properties={"stoxsim.market-data.upstox.stream-enabled=false","stoxsim.market-data.upstox.instrument-sync-on-startup=false","stoxsim.market-data.alpaca.instrument-sync-on-startup=false","stoxsim.market-data.alpaca.polling-enabled=false","stoxsim.billing.reconciliation-enabled=false","stoxsim.billing.test.reconciliation-enabled=false","stoxsim.security.rate-limit.enabled=false","stoxsim.billing.live.approved=true","stoxsim.billing.live.enabled=true","stoxsim.billing.live.key-id=rzp_live_fixture","stoxsim.billing.live.key-secret=fixture-secret","stoxsim.billing.live.webhook-secret=01234567890123456789012345678901","stoxsim.billing.live.plus-plan=plan_plus","stoxsim.billing.live.pro-plan=plan_pro","stoxsim.billing.play.enabled=true","stoxsim.billing.play.plus-product=plus","stoxsim.billing.play.pro-product=pro","stoxsim.billing.play.plus-base-plan=monthly","stoxsim.billing.play.pro-base-plan=monthly","stoxsim.billing.play.credentials-file=/not-used-mocked-provider","stoxsim.billing.play.token-encryption-key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","stoxsim.billing.play.notification-audience=https://api.stoxsim.test/api/v1/billing/play/notifications","stoxsim.billing.play.notification-email=push@fixture.iam.gserviceaccount.com"})
class LiveBillingIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
 @Autowired JdbcTemplate db;@Autowired AppUserRepository users;@Autowired SubscriptionService subscriptions;@Autowired UserSubscriptionRepository entitlementRows;@Autowired VirtualAccountRepository accounts;
 @Autowired LiveBillingService billing;@Autowired BillingAccountGuard guard;@Autowired PurchaseTokenVault vault;@Autowired ObjectMapper json;@Autowired WebApplicationContext context;
 @MockitoBean RazorpayLiveClient razorpay;@MockitoBean GooglePlayClient play;@MockitoBean PlayNotificationVerifier notifications;
 UUID actor,other;ObjectNode remote,payment;Instant end;
 @BeforeEach void setup(){db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");actor=user("buyer");other=user("other");end=Instant.now().plus(Duration.ofDays(30)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);when(razorpay.create(anyString(),any())).thenAnswer(c->{remote=razor(c.getArgument(1),"created",0);return remote;});when(razorpay.fetch(anyString())).thenAnswer(c->remote);when(razorpay.invoices(anyString())).thenAnswer(c->json.readTree("{\"items\":[]}"));}
 UUID user(String label){var user=new AppUser(label+"@stoxsim.test","unused-hash","Buyer");user.markEmailVerified();user=users.saveAndFlush(user);subscriptions.initializeFree(user);return user.getId();}
 ObjectNode razor(UUID id,String state,int count){var n=json.createObjectNode();n.put("id","sub_fixture");n.put("plan_id","plan_plus");n.put("quantity",1);n.put("status",state);n.put("paid_count",count);n.put("current_end",end.getEpochSecond());n.put("remaining_count",11);n.putObject("notes").put("stoxsim_billing_reference",id.toString());return n;}
 void paid(LiveBillingService.Entry entry){remote=razor(entry.id(),"active",1);var invoice=json.createObjectNode();invoice.put("id","inv_fixture");invoice.put("subscription_id","sub_fixture");invoice.put("status","paid");invoice.put("payment_id","pay_fixture");invoice.put("paid_at",Instant.now().getEpochSecond());var list=json.createObjectNode();list.putArray("items").add(invoice);when(razorpay.invoices(anyString())).thenReturn(list);payment=json.createObjectNode();payment.put("id","pay_fixture");payment.put("invoice_id","inv_fixture");payment.put("currency","INR");payment.put("amount",9900);payment.put("amount_refunded",0);payment.put("status","captured");when(razorpay.payment("pay_fixture")).thenAnswer(c->payment);}
 ObjectNode playResource(UUID owner,String state,String ack){var n=json.createObjectNode();n.put("subscriptionState",state);n.put("acknowledgementState",ack);n.putObject("externalAccountIdentifiers").put("obfuscatedExternalAccountId",billing.playConfig(owner).obfuscatedAccountId());var line=n.putArray("lineItems").addObject();line.put("productId","plus");line.put("expiryTime",end.toString());line.put("latestSuccessfulOrderId","GPA.fixture");line.putObject("offerDetails").put("basePlanId","monthly");line.putObject("autoRenewingPlan").put("autoRenewEnabled",true);return n;}
 void expectStatus(int expected,Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(expected));}
 @Test void checkoutReservationsAreRetrySafeAndCannotCrossAccountsOrProviders(){UUID key=UUID.randomUUID();var entry=billing.create(actor,"PLUS",key);assertThat(billing.create(actor,"PLUS",key).id()).isEqualTo(entry.id());verify(razorpay,times(1)).create(anyString(),any());expectStatus(409,()->billing.create(actor,"PLUS",UUID.randomUUID()));expectStatus(409,()->billing.create(actor,"PRO",key));expectStatus(404,()->billing.refresh(other,entry.id()));when(play.fetch("token")).thenReturn(playResource(actor,"SUBSCRIPTION_STATE_ACTIVE","ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"));expectStatus(409,()->billing.verifyPlay(actor,"token","plus"));assertThat(entitlementRows.findByUserId(actor).orElseThrow().effectivePlan()).isEqualTo(SubscriptionPlan.FREE);}
 @Test void ambiguousCreationPersistsAndOnlyMatchingReferenceCanReconcile(){doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Timeout")).when(razorpay).create(anyString(),any());UUID key=UUID.randomUUID();expectStatus(502,()->billing.create(actor,"PLUS",key));var entry=billing.create(actor,"PLUS",key);assertThat(entry.status()).isEqualTo("CREATING");remote=razor(UUID.randomUUID(),"active",1);expectStatus(502,()->billing.reconcile(actor,entry.id(),"sub_fixture"));remote=razor(entry.id(),"created",0);assertThat(billing.reconcile(actor,entry.id(),"sub_fixture").status()).isEqualTo("PENDING");verify(razorpay,times(1)).create(anyString(),any());}
 @Test void capturedPaymentGrantsSeparatePortfoliosAndPreservesCreditsOnReplay(){var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);assertThat(billing.refresh(actor,entry.id()).accessUntil()).isEqualTo(end);assertThat(entitlementRows.findByUserId(actor).orElseThrow().effectivePlan()).isEqualTo(SubscriptionPlan.PLUS);assertThat(accounts.findSandboxesByUserId(actor)).hasSize(2);db.update("UPDATE user_subscription SET scenario_paid_used=3 WHERE user_id=?",actor);billing.refresh(actor,entry.id());assertThat(db.queryForObject("SELECT scenario_paid_used FROM user_subscription WHERE user_id=?",Integer.class,actor)).isEqualTo(3);payment.put("status","authorized");assertThat(billing.refresh(actor,entry.id()).accessUntil()).isNull();}
 @Test void fixedGraceAndOutageCannotExtendExpiredAccess(){var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());remote.put("status","pending");assertThat(billing.refresh(actor,entry.id()).accessUntil()).isEqualTo(end.plus(Duration.ofHours(72)));assertThat(billing.refresh(actor,entry.id()).accessUntil()).isEqualTo(end.plus(Duration.ofHours(72)));db.update("UPDATE billing_purchase SET access_until=?,paid_through=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),Timestamp.from(Instant.now().minus(Duration.ofDays(4))),entry.id());when(razorpay.fetch(anyString())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Unavailable"));billing.reconcileDue();assertThat(entitlementRows.findByUserId(actor).orElseThrow().effectivePlan()).isEqualTo(SubscriptionPlan.FREE);assertThat(accounts.findSandboxesByUserId(actor)).allMatch(a->!a.isActive());}
 @Test void renewalRefillsOnceAndWrongInvoiceCannotGrant(){var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());db.update("UPDATE user_subscription SET scenario_paid_used=3 WHERE user_id=?",actor);end=end.plus(Duration.ofDays(30));remote.put("current_end",end.getEpochSecond()).put("paid_count",2);billing.refresh(actor,entry.id());assertThat(db.queryForObject("SELECT scenario_paid_used FROM user_subscription WHERE user_id=?",Integer.class,actor)).isZero();payment.put("invoice_id","inv_foreign");expectStatus(502,()->billing.refresh(actor,entry.id()));}
 @Test void cancellationRequiresAcknowledgementAndIsIdempotent(){var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());expectStatus(409,()->guard.requireDeletionAllowed(actor));when(razorpay.cancel("sub_fixture",true)).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Timeout"));expectStatus(502,()->billing.cancel(actor,entry.id()));expectStatus(409,()->guard.requireDeletionAllowed(actor));doAnswer(c->remote).when(razorpay).cancel("sub_fixture",true);var canceled=billing.cancel(actor,entry.id());assertThat(canceled.cancellationStatus()).isEqualTo("CONFIRMED");assertThat(canceled.accessUntil()).isEqualTo(end);guard.requireDeletionAllowed(actor);billing.cancel(actor,entry.id());verify(razorpay,times(2)).cancel("sub_fixture",true);}
 @Test void refundRevokesButDoesNotClaimRenewalStopped(){var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());payment.put("amount_refunded",9900).put("status","refunded");assertThat(billing.refresh(actor,entry.id()).status()).isEqualTo("REVOKED");assertThat(entitlementRows.findByUserId(actor).orElseThrow().effectivePlan()).isEqualTo(SubscriptionPlan.FREE);expectStatus(409,()->guard.requireDeletionAllowed(actor));when(razorpay.cancel("sub_fixture",true)).thenReturn(remote);billing.cancel(actor,entry.id());guard.requireDeletionAllowed(actor);}
 @Test void signedWebhookDeduplicatesAndFetchesCurrentState()throws Exception{var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());byte[] raw=json.writeValueAsBytes(Map.of("event","subscription.activated","payload",Map.of("subscription",Map.of("entity",remote))));expectStatus(401,()->billing.razorpayWebhook(raw,"bad","evt_1"));remote.put("status","halted");billing.razorpayWebhook(raw,sign(raw),"evt_1");assertThat(entitlementRows.findByUserId(actor).orElseThrow().effectivePlan()).isEqualTo(SubscriptionPlan.FREE);clearInvocations(razorpay);billing.razorpayWebhook(raw,sign(raw),"evt_1");verify(razorpay,never()).fetch(anyString());}
 @Test void playAcknowledgesEncryptsAndRejectsCrossAccountRestore(){var pending=playResource(actor,"SUBSCRIPTION_STATE_ACTIVE","ACKNOWLEDGEMENT_STATE_PENDING");var ack=playResource(actor,"SUBSCRIPTION_STATE_ACTIVE","ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED");when(play.fetch("opaque-token")).thenReturn(pending,ack);var entry=billing.verifyPlay(actor,"opaque-token","plus");verify(play).acknowledge("plus","opaque-token");assertThat(entry.accessUntil()).isEqualTo(end);assertThat(entry.providerReference()).isNull();String encrypted=db.queryForObject("SELECT encrypted_token FROM billing_purchase WHERE id=?",String.class,entry.id());assertThat(encrypted).doesNotContain("opaque-token");assertThat(vault.decrypt(encrypted)).isEqualTo("opaque-token");expectStatus(403,()->billing.verifyPlay(other,"opaque-token","plus"));assertThat(guard.export(actor).toString()).doesNotContain(encrypted);}
 @Test void playPendingAndExpiredNeverGrantAccess(){var remote=playResource(actor,"SUBSCRIPTION_STATE_PENDING","ACKNOWLEDGEMENT_STATE_PENDING");when(play.fetch("pending-token")).thenAnswer(c->remote);var entry=billing.verifyPlay(actor,"pending-token","plus");assertThat(entry.accessUntil()).isNull();verify(play,never()).acknowledge(anyString(),anyString());remote.put("subscriptionState","SUBSCRIPTION_STATE_ACTIVE").put("acknowledgementState","ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED");assertThat(billing.refresh(actor,entry.id()).accessUntil()).isEqualTo(end);remote.put("subscriptionState","SUBSCRIPTION_STATE_EXPIRED");assertThat(billing.refresh(actor,entry.id()).accessUntil()).isNull();}
 @Test void testPurchasesAndIncorrectBasePlansCannotGrantProductionBenefits(){var remote=playResource(actor,"SUBSCRIPTION_STATE_ACTIVE","ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED");remote.putObject("testPurchase");when(play.fetch(anyString())).thenReturn(remote);expectStatus(409,()->billing.verifyPlay(actor,"test-token","plus"));remote.remove("testPurchase");((ObjectNode)remote.path("lineItems").get(0).path("offerDetails")).put("basePlanId","wrong");expectStatus(409,()->billing.verifyPlay(actor,"wrong-product","plus"));assertThat(db.queryForObject("SELECT count(*) FROM billing_purchase",Integer.class)).isZero();}
 @Test void localDeadlineProtectsAccountsWithoutReconciliation(){var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());db.update("UPDATE user_subscription SET billing_access_until=now()-interval '1 second' WHERE user_id=?",actor);db.update("UPDATE virtual_account SET billing_access_until=now()-interval '1 second' WHERE user_id=?",actor);assertThat(entitlementRows.findByUserId(actor).orElseThrow().effectivePlan()).isEqualTo(SubscriptionPlan.FREE);assertThat(accounts.findSandboxesByUserId(actor)).allMatch(a->!a.isActive());expectStatus(403,()->billing.revenue(actor));}
 @Test void authoritativeVoidedOrderRevokesEvenWhenSubscriptionStillActive()throws Exception{var remote=playResource(actor,"SUBSCRIPTION_STATE_ACTIVE","ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED");when(play.fetch("token")).thenReturn(remote);var entry=billing.verifyPlay(actor,"token","plus");byte[] payload=json.writeValueAsBytes(Map.of("packageName","com.stoxsim.app","voidedPurchaseNotification",Map.of("purchaseToken","token","orderId","GPA.fixture")));byte[] raw=json.writeValueAsBytes(Map.of("message",Map.of("messageId","play_1","data",Base64.getEncoder().encodeToString(payload))));expectStatus(502,()->billing.playNotification(raw));when(play.wasVoided("token","GPA.fixture")).thenReturn(true);billing.playNotification(raw);assertThat(billing.overview(actor).entries().getFirst().status()).isEqualTo("REVOKED");assertThat(entitlementRows.findByUserId(actor).orElseThrow().effectivePlan()).isEqualTo(SubscriptionPlan.FREE);clearInvocations(play);billing.playNotification(raw);verify(play,never()).fetch(anyString());}
 @Test void publicCallbackIsExactPostAndStillRequiresOidc()throws Exception{var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Unverified Play notification")).when(notifications).verify("Bearer invalid");mvc.perform(post("/api/v1/billing/play/notifications").header("Authorization","Bearer invalid").content("{}")).andExpect(status().isUnauthorized());verify(notifications).verify("Bearer invalid");mvc.perform(get("/api/v1/billing/play/notifications")).andExpect(status().isUnauthorized());mvc.perform(post("/api/v1/billing/play/verify").content("{}")).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/billing")).andExpect(status().isUnauthorized());mvc.perform(post("/api/v1/billing/razorpay/webhook").content("{}")).andExpect(status().isUnauthorized());}
 @Test void verifiedGraceUsesTheSameDeadlineForFinwizAndSandboxesWithoutRefillingCredits(){
  var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());
  var expired=Instant.now().minus(Duration.ofHours(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
  db.update("UPDATE billing_purchase SET paid_through=? WHERE id=?",Timestamp.from(expired),entry.id());
  db.update("UPDATE user_subscription SET current_period_end=?,scenario_paid_used=3 WHERE user_id=?",Timestamp.from(expired),actor);
  remote.put("status","pending");billing.refresh(actor,entry.id());
  var subscription=entitlementRows.findByUserId(actor).orElseThrow();
  assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(expired);
  assertThat(subscription.effectiveFinwizPlan(Instant.now())).isEqualTo(SubscriptionPlan.PLUS);
  assertThat(accounts.findSandboxesByUserId(actor)).allMatch(a->a.isActive());
  assertThat(db.queryForObject("SELECT scenario_paid_used FROM user_subscription WHERE user_id=?",Integer.class,actor)).isEqualTo(3);
 }
 @Test void liveAccessClearsAnExpiredTestSandboxDeadline(){
  var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());
  db.update("UPDATE virtual_account SET test_trading_until=now()-interval '1 day' WHERE user_id=? AND account_kind='SANDBOX'",actor);
  assertThat(accounts.findSandboxesByUserId(actor)).allMatch(a->!a.isActive());
  billing.refresh(actor,entry.id());
  assertThat(accounts.findSandboxesByUserId(actor)).allSatisfy(a->{assertThat(a.isActive()).isTrue();assertThat(a.getTestTradingUntil()).isNull();});
 }
 @Test void restartedPlayRenewalClearsCancellationAndBlocksDeletion(){
  var remote=playResource(actor,"SUBSCRIPTION_STATE_ACTIVE","ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED");
  when(play.fetch("restart-token")).thenAnswer(c->remote);
  var entry=billing.verifyPlay(actor,"restart-token","plus");
  remote.put("subscriptionState","SUBSCRIPTION_STATE_CANCELED");
  assertThat(billing.refresh(actor,entry.id()).cancellationStatus()).isEqualTo("CONFIRMED");
  guard.requireDeletionAllowed(actor);
  remote.put("subscriptionState","SUBSCRIPTION_STATE_ACTIVE");
  // Deletion must detect a provider restart even before any RTDN or manual refresh.
  expectStatus(409,()->guard.requireDeletionAllowed(actor));
  assertThat(billing.overview(actor).entries().getFirst().cancellationStatus()).isEqualTo("NONE");
  db.update("UPDATE billing_purchase SET cancellation_status='CONFIRMED' WHERE id=?",entry.id());
  expectStatus(409,()->guard.requireDeletionAllowed(actor));
 }
 @Test void refundForAnUnobservedOlderInvoiceResolvesItsOwnerAndRevokesAccess()throws Exception{
  historicalChargeNotice("refund.processed",true);
 }
 @Test void disputeForAnUnobservedOlderInvoiceResolvesItsOwnerAndRevokesAccess()throws Exception{
  historicalChargeNotice("payment.dispute.created",false);
 }
 void historicalChargeNotice(String event,boolean refunded)throws Exception{
  var entry=billing.create(actor,"PLUS",UUID.randomUUID());paid(entry);billing.refresh(actor,entry.id());
  var older=payment.deepCopy();older.put("id","pay_older").put("invoice_id","inv_older");
  if(refunded)older.put("amount_refunded",9900).put("status","refunded");
  when(razorpay.payment("pay_older")).thenReturn(older);
  var invoice=json.createObjectNode().put("id","inv_older").put("payment_id","pay_older").put("subscription_id","sub_fixture").put("status","paid").put("paid_at",Instant.now().minus(Duration.ofDays(30)).getEpochSecond());
  when(razorpay.invoice("inv_older")).thenReturn(invoice);
  var entity=json.createObjectNode().put("id",refunded?"rfnd_older":"disp_older").put("payment_id","pay_older").put("status","open");
  if(!refunded)when(razorpay.dispute("disp_older")).thenReturn(entity);
  byte[] raw=json.writeValueAsBytes(Map.of("event",event,"payload",Map.of(refunded?"refund":"dispute",Map.of("entity",entity))));
  billing.razorpayWebhook(raw,sign(raw),"evt_historical");
  assertThat(billing.overview(actor).entries().getFirst().status()).isEqualTo("REVOKED");
  assertThat(entitlementRows.findByUserId(actor).orElseThrow().effectivePlan()).isEqualTo(SubscriptionPlan.FREE);
  assertThat(db.queryForObject("SELECT purchase_id FROM billing_charge WHERE charge_id='pay_older'",UUID.class)).isEqualTo(entry.id());
  assertThat(billing.overview(other).entries()).isEmpty();
 }
 String sign(byte[] bytes)throws Exception{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("01234567890123456789012345678901".getBytes(),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(bytes));}
}
