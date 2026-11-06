package com.stoxsim.billing;

import java.sql.*;
import java.time.*;
import java.util.*;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.subscription.domain.*;
import com.stoxsim.subscription.provider.*;
import com.stoxsim.subscription.service.SubscriptionService;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.order.service.SandboxOrderCleanup;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Provider-fetched, ownership-verified purchases are the only source of paid access. */
@Service
public class LiveBillingService {
    public record Entry(UUID id,String provider,String plan,String status,Instant currentPeriodEnd,Instant accessUntil,String cancellationStatus,String providerReference,String managementUrl) {}
    public record Overview(boolean enabled,boolean razorpayEnabled,boolean googlePlayEnabled,String keyId,List<Entry> entries,String historyCoverage) {}
    public record PlayProduct(String plan,String productId,String basePlanId) {}
    public record PlayConfig(boolean enabled,String packageName,String obfuscatedAccountId,List<PlayProduct> products) {}
    private record Row(UUID id,UUID userId,String provider,String plan,String reference,String product,String encryptedToken,String status,Instant paidThrough,Instant accessUntil,int paidCount,String cancellation,Instant cancelAt,Integer remaining,boolean revoked) {}
    private final JdbcTemplate db;private final AppUserRepository users;private final BillingConfig config;
    private final RazorpayLiveClient razorpay;private final GooglePlayClient play;private final PurchaseTokenVault vault;
    private final ObjectMapper json;private final TransactionTemplate tx;private final SubscriptionService subscriptions;
    private final UserSubscriptionRepository subscriptionRows;private final VirtualAccountRepository accounts;private final SandboxOrderCleanup cleanup;private final Clock clock;
    public LiveBillingService(JdbcTemplate db,AppUserRepository users,BillingConfig config,RazorpayLiveClient razorpay,GooglePlayClient play,PurchaseTokenVault vault,ObjectMapper json,PlatformTransactionManager manager,SubscriptionService subscriptions,UserSubscriptionRepository subscriptionRows,VirtualAccountRepository accounts,SandboxOrderCleanup cleanup,Clock clock){
        this.db=db;this.users=users;this.config=config;this.razorpay=razorpay;this.play=play;this.vault=vault;this.json=json;tx=new TransactionTemplate(manager);tx.setTimeout(60);this.subscriptions=subscriptions;this.subscriptionRows=subscriptionRows;this.accounts=accounts;this.cleanup=cleanup;this.clock=clock;}
    private void learner(UUID actor){var user=users.findById(actor).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Sign in again"));if(!user.isEmailVerified())throw error(HttpStatus.FORBIDDEN,"Verify your email before purchasing");}
    public Overview overview(UUID actor){return new Overview(config.razorpayEnabled||config.playEnabled,config.razorpayEnabled,config.playEnabled,config.razorpayEnabled?config.keyId:null,db.query("SELECT * FROM billing_purchase WHERE user_id=? ORDER BY created_at DESC LIMIT 50",this::map,actor).stream().map(this::view).toList(),"Observed subscription records only. Verified charge totals are not a complete provider settlement statement.");}
    public PlayConfig playConfig(UUID actor){
        if(!config.playEnabled)return new PlayConfig(false,config.packageName,null,List.of());learner(actor);
        String reference=tx.execute(s->{users.findByIdForUpdate(actor).orElseThrow();return accountReference(actor);});
        return new PlayConfig(true,config.packageName,reference,List.of(new PlayProduct("PLUS",config.plusProduct,config.plusBasePlan),new PlayProduct("PRO",config.proProduct,config.proBasePlan)));}
    private String accountReference(UUID actor){db.update("INSERT INTO billing_customer(user_id,account_reference) VALUES (?,?) ON CONFLICT(user_id) DO NOTHING",actor,UUID.randomUUID());return db.queryForObject("SELECT account_reference::text FROM billing_customer WHERE user_id=?",String.class,actor);}
    public Entry create(UUID actor,String plan,UUID key){
        learner(actor);requireRazorpay();if(!Set.of("PLUS","PRO").contains(plan))throw error(HttpStatus.BAD_REQUEST,"Choose Plus or Pro");razorpay.validatePlan(plan);
        record Reservation(Row row,boolean fresh) {}
        var reservation=tx.execute(s->{users.findByIdForUpdate(actor).orElseThrow();
            var prior=db.query("SELECT * FROM billing_purchase WHERE user_id=? AND request_key=?",this::map,actor,key);
            if(!prior.isEmpty()){Row item=prior.getFirst();if(!item.plan.equals(plan)||!item.provider.equals("RAZORPAY"))throw error(HttpStatus.CONFLICT,"Retry key belongs to another purchase");return new Reservation(item,false);}
            noOtherPurchase(actor,null);UUID id=UUID.randomUUID();db.update("INSERT INTO billing_purchase(id,user_id,provider,plan,request_key,provider_product) VALUES (?,?,'RAZORPAY',?,?,?)",id,actor,plan,key,config.razorpayPlan(plan));accountReference(actor);return new Reservation(row(id),true);});
        if(!reservation.fresh())return view(reservation.row());
        // Persist first: a timeout never triggers an automatic duplicate creation.
        JsonNode created=razorpay.create(plan,reservation.row().id);
        return tx.execute(s->applyRazorpay(lock(reservation.row().id),razorpay.fetch(created.path("id").asText())));}
    public Entry refresh(UUID actor,UUID id){return tx.execute(s->refreshLocked(owned(actor,id)));}
    public Entry reconcile(UUID actor,UUID id,String reference){learner(actor);requireRazorpay();return tx.execute(s->{Row item=owned(actor,id);if(!item.provider.equals("RAZORPAY"))throw error(HttpStatus.CONFLICT,"Wrong provider");return applyRazorpay(item,razorpay.fetch(reference));});}
    public Entry cancel(UUID actor,UUID id){
        requireRazorpay();tx.executeWithoutResult(s->{Row item=owned(actor,id);if(!item.provider.equals("RAZORPAY"))throw error(HttpStatus.CONFLICT,"Manage this subscription in Google Play");if(item.reference==null)throw error(HttpStatus.CONFLICT,"Reconcile the pending checkout before cancellation");
            applyRazorpay(item,razorpay.fetch(item.reference));item=row(id);
            if(!item.cancellation.equals("NONE")||Boolean.TRUE.equals(db.queryForObject("SELECT renewal_stopped FROM billing_purchase WHERE id=?",Boolean.class,id)))return;
            db.update("UPDATE billing_purchase SET cancellation_status='REQUESTED',cancel_at=paid_through,updated_at=now() WHERE id=?",id);});
        return tx.execute(s->{Row item=owned(actor,id);if(!item.cancellation.equals("REQUESTED"))return view(item);
            boolean atEnd=item.cancelAt!=null&&item.cancelAt.isAfter(clock.instant())&&!Integer.valueOf(0).equals(item.remaining);
            JsonNode acknowledged=razorpay.cancel(item.reference,atEnd);validateRazorpayIdentity(item,acknowledged);
            db.update("UPDATE billing_purchase SET cancellation_status='CONFIRMED' WHERE id=?",id);return applyRazorpay(row(id),acknowledged);});}
    public Entry verifyPlay(UUID actor,String token,String product){
        learner(actor);requirePlay();String hash=vault.hash(token);
        return tx.execute(s->{users.findByIdForUpdate(actor).orElseThrow();JsonNode remote=play.fetch(token);String plan=validatePlay(actor,product,remote);
            var found=db.query("SELECT * FROM billing_purchase WHERE token_hash=? FOR UPDATE",this::map,hash);Row item;
            if(!found.isEmpty()){item=found.getFirst();if(!item.userId.equals(actor))throw error(HttpStatus.NOT_FOUND,"Purchase not found");if(!item.plan.equals(plan)||!item.product.equals(product))throw error(HttpStatus.CONFLICT,"Purchase product changed");}
            else{noOtherPurchase(actor,null);if(!remote.path("linkedPurchaseToken").asText().isBlank())throw error(HttpStatus.CONFLICT,"Linked subscription changes require support reconciliation");
                UUID id=UUID.randomUUID();db.update("INSERT INTO billing_purchase(id,user_id,provider,plan,request_key,provider_product,provider_reference,token_hash,encrypted_token) VALUES (?,?,'GOOGLE_PLAY',?,?,?,?,?,?)",id,actor,plan,UUID.randomUUID(),product,"play_"+hash,hash,vault.encrypt(token));item=row(id);}
            if(Set.of("SUBSCRIPTION_STATE_ACTIVE","SUBSCRIPTION_STATE_IN_GRACE_PERIOD","SUBSCRIPTION_STATE_CANCELED").contains(remote.path("subscriptionState").asText())&&remote.path("acknowledgementState").asText().equals("ACKNOWLEDGEMENT_STATE_PENDING")){
                play.acknowledge(product,token);remote=play.fetch(token);validatePlay(actor,product,remote);}
            return applyPlay(item,remote);});}
    private String validatePlay(UUID actor,String product,JsonNode remote){
        String plan=product.equals(config.plusProduct)?"PLUS":product.equals(config.proProduct)?"PRO":null;
        if(plan==null||!remote.path("externalAccountIdentifiers").path("obfuscatedExternalAccountId").asText().equals(accountReference(actor)))throw error(HttpStatus.FORBIDDEN,"Purchase belongs to another account or product");
        if(remote.has("testPurchase"))throw error(HttpStatus.CONFLICT,"License test purchases cannot grant production entitlements");
        JsonNode lines=remote.path("lineItems");if(!lines.isArray()||lines.size()!=1)throw error(HttpStatus.CONFLICT,"Purchase must contain one supported subscription");
        JsonNode line=lines.get(0);if(!line.path("productId").asText().equals(product)||!line.path("offerDetails").path("basePlanId").asText().equals(config.playBasePlan(plan))||!line.path("autoRenewingPlan").isObject()||line.path("autoRenewingPlan").has("installmentDetails"))throw error(HttpStatus.CONFLICT,"Purchase does not match the server catalog");return plan;}
    private Entry applyPlay(Row item,JsonNode remote){
        validatePlay(item.userId,item.product,remote);JsonNode line=remote.path("lineItems").get(0);Instant end=parseInstant(line.path("expiryTime").asText());String state=remote.path("subscriptionState").asText();
        String status=switch(state){case "SUBSCRIPTION_STATE_ACTIVE"->"ACTIVE";case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"->"GRACE";case "SUBSCRIPTION_STATE_CANCELED"->"ENDING";case "SUBSCRIPTION_STATE_EXPIRED","SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED"->"EXPIRED";case "SUBSCRIPTION_STATE_PENDING"->"PENDING";case "SUBSCRIPTION_STATE_PAUSED","SUBSCRIPTION_STATE_ON_HOLD"->"PAST_DUE";default->throw error(HttpStatus.BAD_GATEWAY,"Unknown subscription state");};
        boolean acknowledged=remote.path("acknowledgementState").asText().equals("ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED");Instant access=null;
        if(Set.of("ACTIVE","GRACE","ENDING").contains(status)&&acknowledged&&end!=null&&end.isAfter(clock.instant()))access=end;
        if(access==null&&Set.of("ACTIVE","GRACE","ENDING").contains(status))status=end!=null&&!end.isAfter(clock.instant())?"EXPIRED":"PENDING";
        boolean voided=play.wasVoided(vault.decrypt(item.encryptedToken),line.path("latestSuccessfulOrderId").asText());
        if(voided)db.update("UPDATE billing_purchase SET revoked=true WHERE id=?",item.id);
        if(item.revoked||voided){status="REVOKED";access=null;}
        Instant paid=state.equals("SUBSCRIPTION_STATE_IN_GRACE_PERIOD")?item.paidThrough:(acknowledged?end:item.paidThrough);
        boolean stopped=Set.of("SUBSCRIPTION_STATE_CANCELED","SUBSCRIPTION_STATE_EXPIRED","SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED").contains(state);
        db.update("UPDATE billing_purchase SET status=?,paid_through=?,access_until=?,cancellation_status=?,renewal_stopped=?,updated_at=now() WHERE id=?",status,ts(paid),ts(access),state.equals("SUBSCRIPTION_STATE_CANCELED")?"CONFIRMED":item.cancellation,stopped,item.id);
        project(item.id);return view(row(item.id));}
    private void validateRazorpayIdentity(Row item,JsonNode remote){String reference=remote.path("id").asText();
        if(!reference.matches("sub_[A-Za-z0-9]+")||(item.reference!=null&&!item.reference.equals(reference))||!remote.path("plan_id").asText().equals(item.product)||remote.path("quantity").asInt()!=1||!remote.path("notes").path("stoxsim_billing_reference").asText().equals(item.id.toString()))throw error(HttpStatus.BAD_GATEWAY,"Provider subscription does not match checkout");}
    private Entry applyRazorpay(Row item,JsonNode remote){
        requireRazorpay();validateRazorpayIdentity(item,remote);String reference=remote.path("id").asText(),state=remote.path("status").asText();
        if(!Set.of("created","authenticated","active","pending","halted","paused","cancelled","completed","expired").contains(state))throw error(HttpStatus.BAD_GATEWAY,"Unknown subscription state");
        Instant end=epoch(remote.path("current_end").asLong(0));int paidCount=remote.path("paid_count").asInt(0);boolean captured=verifyLatestCharge(item,reference);Instant paid=item.paidThrough;
        if(state.equals("active")&&captured&&end!=null&&paidCount>item.paidCount)paid=end;
        String cancellation=item.cancellation;if(cancellation.equals("REQUESTED")&&Set.of("cancelled","completed","expired").contains(state))cancellation="CONFIRMED";
        String status=switch(state){case "active"->"ACTIVE";case "pending"->"GRACE";case "created","authenticated"->"PENDING";case "cancelled","completed","expired"->"CANCELED";default->"PAST_DUE";};
        Instant access=null;if(paid!=null&&state.equals("active")&&captured)access=paid;if(paid!=null&&state.equals("pending")&&captured)access=paid.plus(Duration.ofHours(72));
        if(cancellation.equals("CONFIRMED")&&item.cancelAt!=null&&paid!=null){access=item.cancelAt.isBefore(paid)?item.cancelAt:paid;status="ENDING";}
        if(Boolean.TRUE.equals(db.queryForObject("SELECT revoked FROM billing_purchase WHERE id=?",Boolean.class,item.id))){access=null;status="REVOKED";}
        if(access!=null&&!access.isAfter(clock.instant())){access=null;status="EXPIRED";}
        boolean stopped=cancellation.equals("CONFIRMED")||Set.of("cancelled","completed","expired").contains(state);
        db.update("UPDATE billing_purchase SET provider_reference=?,status=?,paid_through=?,access_until=?,verified_paid_count=?,cancellation_status=?,remaining_cycles=?,renewal_stopped=?,updated_at=now() WHERE id=?",reference,status,ts(paid),ts(access),paidCount>item.paidCount&&captured?paidCount:item.paidCount,cancellation,remote.path("remaining_count").isInt()?remote.path("remaining_count").asInt():null,stopped,item.id);
        project(item.id);return view(row(item.id));}
    /** Charge coverage is explicitly limited to invoices observed by this bounded reconciliation. */
    private boolean verifyLatestCharge(Row item,String reference){
        JsonNode invoices=razorpay.invoices(reference).path("items");if(!invoices.isArray()||invoices.size()>100)throw error(HttpStatus.BAD_GATEWAY,"Invalid invoice response");JsonNode latest=null;
        for(JsonNode invoice:invoices){if(!invoice.path("subscription_id").asText().equals(reference))throw error(HttpStatus.BAD_GATEWAY,"Invoice ownership mismatch");if(!invoice.path("status").asText().equals("paid"))continue;if(latest==null||invoice.path("paid_at").asLong()>latest.path("paid_at").asLong())latest=invoice;}
        if(latest==null)return false;return verifyCharge(item,reference,latest,razorpay.payment(latest.path("payment_id").asText()));}
    private boolean verifyCharge(Row item,String reference,JsonNode latest,JsonNode payment){
        String paymentId=latest.path("payment_id").asText();
        if(!latest.path("subscription_id").asText().equals(reference)||!latest.path("status").asText().equals("paid"))throw error(HttpStatus.BAD_GATEWAY,"Invoice ownership mismatch");
        if(!payment.path("id").asText().equals(paymentId)||!payment.path("invoice_id").asText().equals(latest.path("id").asText())||!payment.path("currency").asText().equals("INR")||payment.path("amount").asLong()!=RazorpayLiveClient.amount(item.plan))throw error(HttpStatus.BAD_GATEWAY,"Payment does not match catalog invoice");
        long amount=payment.path("amount").asLong(),refunded=payment.path("amount_refunded").asLong(0);if(refunded<0||refunded>amount)throw error(HttpStatus.BAD_GATEWAY,"Invalid refund amount");String state=payment.path("status").asText();
        db.update("INSERT INTO billing_charge(provider,charge_id,purchase_id,amount_minor,refunded_minor,currency,status,paid_at) VALUES ('RAZORPAY',?,?,?,?,?,?,?) ON CONFLICT(provider,charge_id) DO UPDATE SET refunded_minor=excluded.refunded_minor,status=excluded.status,updated_at=now()",paymentId,item.id,amount,refunded,"INR",state,ts(epoch(latest.path("paid_at").asLong(0))));
        if(refunded>0||state.equals("refunded"))db.update("UPDATE billing_purchase SET revoked=true WHERE id=?",item.id);return state.equals("captured")&&refunded==0;}
    private void project(UUID id){
        Row item=row(id);Instant until=item.accessUntil;boolean active=until!=null&&until.isAfter(clock.instant());var current=subscriptionRows.findByUserIdForUpdate(item.userId).orElseThrow();
        if(!Objects.equals(current.getProviderSubscriptionReference(),item.id.toString())&&!active&&current.getBillingProvider()!=null&&!current.isTestBilling())return;
        if(active)noOtherPurchase(item.userId,item.id);
        subscriptions.applyProviderUpdate(new BillingSubscriptionUpdate(item.userId,SubscriptionPlan.valueOf(item.plan),active?SubscriptionStatus.ACTIVE:(terminal(item.status)?SubscriptionStatus.CANCELED:SubscriptionStatus.PAST_DUE),item.provider,accountReference(item.userId),item.id.toString(),item.paidThrough));
        current.setBillingAccessUntil(until);
        for(UUID accountId:accounts.findSandboxesByUserId(item.userId).stream().map(a->a.getId()).sorted().toList()){
            var account=accounts.findByIdForUpdate(accountId).orElseThrow();
            if(active&&account.getSandboxPlan()==SubscriptionPlan.valueOf(item.plan)){account.setTestTradingUntil(null);account.setBillingAccessUntil(until);account.activate();}
            else{account.deactivate();cleanup.cancelOpenForAccount(account);}}
    }
    private Entry refreshLocked(Row item){if(item.provider.equals("RAZORPAY")){requireRazorpay();if(item.reference==null)throw error(HttpStatus.CONFLICT,"Checkout requires provider reconciliation");return applyRazorpay(item,razorpay.fetch(item.reference));}requirePlay();return applyPlay(item,play.fetch(vault.decrypt(item.encryptedToken)));}
    private void noOtherPurchase(UUID actor,UUID except){
        Integer count=db.queryForObject("SELECT count(*) FROM billing_purchase WHERE user_id=? AND (?::uuid IS NULL OR id<>?) AND (status NOT IN ('CANCELED','EXPIRED','REVOKED') OR access_until>now() OR renewal_stopped=false)",Integer.class,actor,except,except);
        if(count>0)throw error(HttpStatus.CONFLICT,"Manage the existing subscription before starting another purchase");
        var current=subscriptionRows.findByUserIdForUpdate(actor).orElseThrow();if(current.getBillingProvider()!=null&&!Set.of("RAZORPAY","GOOGLE_PLAY").contains(current.getBillingProvider())&&current.effectivePlan()!=SubscriptionPlan.FREE)throw error(HttpStatus.CONFLICT,"Existing subscription must end before purchasing");}
    public void reconcileDue(){
        var ids=db.query("SELECT id FROM billing_purchase WHERE renewal_stopped=false OR access_until IS NOT NULL ORDER BY updated_at LIMIT 20",(rs,n)->rs.getObject(1,UUID.class));
        for(UUID id:ids)try{tx.executeWithoutResult(s->refreshLocked(lock(id)));}catch(RuntimeException ex){
            try{tx.executeWithoutResult(s->{Row item=lock(id);if(item.accessUntil!=null&&!item.accessUntil.isAfter(clock.instant())){db.update("UPDATE billing_purchase SET access_until=NULL,status='EXPIRED' WHERE id=?",id);project(id);}db.update("UPDATE billing_purchase SET updated_at=now() WHERE id=?",id);});}
            catch(RuntimeException ignored){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Billing reconciliation requires review for local reference {}",id);}}}
    public void razorpayWebhook(byte[] raw,String signature,String eventId){
        requireRazorpay();if(!RazorpaySignature.valid(raw,signature,config.webhookSecret))throw error(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");requireEventId(eventId);JsonNode event=parseJson(raw);String type=event.path("event").asText();UUID id=null;JsonNode recoveredInvoice=null,recoveredPayment=null;
        String reference=event.path("payload").path("subscription").path("entity").path("id").asText();
        if(type.startsWith("subscription.")){
            var found=db.query("SELECT id FROM billing_purchase WHERE provider='RAZORPAY' AND provider_reference=?",(rs,n)->rs.getObject(1,UUID.class),reference);if(!found.isEmpty())id=found.getFirst();
            else try{UUID proposed=UUID.fromString(event.path("payload").path("subscription").path("entity").path("notes").path("stoxsim_billing_reference").asText());if(db.queryForObject("SELECT count(*) FROM billing_purchase WHERE id=? AND provider='RAZORPAY'",Integer.class,proposed)>0)id=proposed;}catch(IllegalArgumentException ignored){}
        }else if(type.equals("refund.processed")||type.startsWith("payment.dispute.")){
            String payment=event.path("payload").path(type.equals("refund.processed")?"refund":"dispute").path("entity").path("payment_id").asText();var found=db.query("SELECT purchase_id FROM billing_charge WHERE provider='RAZORPAY' AND charge_id=?",(rs,n)->rs.getObject(1,UUID.class),payment);if(!found.isEmpty())id=found.getFirst();
            else {
                // A webhook may refer to an earlier cycle that bounded polling never observed.
                // Resolve its payment -> invoice -> locally owned subscription using provider reads.
                recoveredPayment=razorpay.payment(payment);
                recoveredInvoice=razorpay.invoice(recoveredPayment.path("invoice_id").asText());
                if(!recoveredPayment.path("id").asText().equals(payment)
                    ||!recoveredInvoice.path("id").asText().equals(recoveredPayment.path("invoice_id").asText())
                    ||!recoveredInvoice.path("payment_id").asText().equals(payment))throw error(HttpStatus.BAD_GATEWAY,"Payment invoice mismatch");
                var purchases=db.query("SELECT id FROM billing_purchase WHERE provider='RAZORPAY' AND provider_reference=?",(rs,n)->rs.getObject(1,UUID.class),recoveredInvoice.path("subscription_id").asText());
                if(!purchases.isEmpty())id=purchases.getFirst();
            }
        }else return;
        if(id==null)return;UUID target=id;JsonNode historicalInvoice=recoveredInvoice,historicalPayment=recoveredPayment;
        tx.executeWithoutResult(s->{Row item=lock(target);if(seen("RAZORPAY",eventId))return;
            if(historicalInvoice!=null)verifyCharge(item,item.reference,historicalInvoice,historicalPayment);
            if(type.equals("refund.processed")){
                String paymentId=event.path("payload").path("refund").path("entity").path("payment_id").asText();JsonNode payment=razorpay.payment(paymentId);long refunded=payment.path("amount_refunded").asLong();
                Long amount=db.queryForObject("SELECT amount_minor FROM billing_charge WHERE provider='RAZORPAY' AND charge_id=? AND purchase_id=?",Long.class,paymentId,target);
                if(!payment.path("id").asText().equals(paymentId)||amount==null||refunded<=0||refunded>amount)throw error(HttpStatus.BAD_GATEWAY,"Refund not confirmed by provider");
                db.update("UPDATE billing_charge SET refunded_minor=?,status=?,updated_at=now() WHERE provider='RAZORPAY' AND charge_id=?",refunded,payment.path("status").asText(),paymentId);db.update("UPDATE billing_purchase SET revoked=true WHERE id=?",target);
            }
            if(type.startsWith("payment.dispute.")){
                JsonNode claim=event.path("payload").path("dispute").path("entity"),dispute=razorpay.dispute(claim.path("id").asText());
                if(!dispute.path("id").asText().equals(claim.path("id").asText())||!dispute.path("payment_id").asText().equals(claim.path("payment_id").asText()))throw error(HttpStatus.BAD_GATEWAY,"Dispute not confirmed by provider");
                if(!dispute.path("status").asText().equals("won"))db.update("UPDATE billing_purchase SET revoked=true WHERE id=?",target);
            }
            applyRazorpay(row(target),razorpay.fetch(item.reference==null?reference:item.reference));recordEvent("RAZORPAY",eventId,target,type);
        });}
    public void playNotification(byte[] raw){
        requirePlay();JsonNode envelope=parseJson(raw);String eventId=envelope.path("message").path("messageId").asText();requireEventId(eventId);byte[] decoded;
        try{decoded=Base64.getDecoder().decode(envelope.path("message").path("data").asText());}catch(IllegalArgumentException ex){throw error(HttpStatus.BAD_REQUEST,"Invalid notification data");}
        if(decoded.length>65536)throw error(HttpStatus.PAYLOAD_TOO_LARGE,"Notification too large");JsonNode event=parseJson(decoded);
        if(!event.path("packageName").asText().equals(config.packageName))throw error(HttpStatus.BAD_REQUEST,"Wrong application notification");if(event.has("testNotification"))return;
        JsonNode notification=event.has("subscriptionNotification")?event.path("subscriptionNotification"):event.path("voidedPurchaseNotification");String token=notification.path("purchaseToken").asText();
        var found=db.query("SELECT id FROM billing_purchase WHERE provider='GOOGLE_PLAY' AND token_hash=?",(rs,n)->rs.getObject(1,UUID.class),vault.hash(token));if(found.isEmpty())return;UUID id=found.getFirst();
        tx.executeWithoutResult(s->{Row item=lock(id);if(seen("GOOGLE_PLAY",eventId))return;
            if(event.has("voidedPurchaseNotification")){if(!play.wasVoided(token,notification.path("orderId").asText()))throw error(HttpStatus.BAD_GATEWAY,"Voided payment not yet confirmed by Google Play");db.update("UPDATE billing_purchase SET revoked=true WHERE id=?",id);item=row(id);}
            applyPlay(item,play.fetch(token));recordEvent("GOOGLE_PLAY",eventId,id,event.has("voidedPurchaseNotification")?"voided-purchase":"subscription");});}
    public Map<String,Object> revenue(UUID actor){var user=users.findById(actor).orElseThrow();if(!user.isPlatformAdmin()||!user.isEmailVerified())throw error(HttpStatus.FORBIDDEN,"Verified administrator required");
        Map<String,Object> result=new LinkedHashMap<>();result.put("observedAt",clock.instant());result.put("currency","INR");result.put("activeSubscriptions",db.queryForObject("SELECT count(*) FROM billing_purchase WHERE access_until>now()",Long.class));
        result.put("razorpayCapturedMinor",db.queryForObject("SELECT coalesce(sum(amount_minor),0) FROM billing_charge WHERE provider='RAZORPAY' AND currency='INR' AND status IN ('captured','refunded')",Long.class));
        result.put("razorpayRefundedMinor",db.queryForObject("SELECT coalesce(sum(refunded_minor),0) FROM billing_charge WHERE provider='RAZORPAY' AND currency='INR'",Long.class));
        result.put("failedRenewals",db.queryForObject("SELECT count(*) FROM billing_purchase WHERE status IN ('GRACE','PAST_DUE')",Long.class));result.put("cancellations",db.queryForObject("SELECT count(*) FROM billing_purchase WHERE cancellation_status='CONFIRMED'",Long.class));
        result.put("notice","Observed verified charges only. Missing historic cycles, Play settlement revenue, taxes and fees are not inferred from entitlements.");return result;}
    private Row owned(UUID actor,UUID id){if(db.queryForObject("SELECT count(*) FROM billing_purchase WHERE id=? AND user_id=?",Integer.class,id,actor)==0)throw error(HttpStatus.NOT_FOUND,"Purchase not found");return lock(id);}
    private Row lock(UUID id){Row item=row(id);users.findByIdForUpdate(item.userId).orElseThrow();return db.query("SELECT * FROM billing_purchase WHERE id=? FOR UPDATE",this::map,id).getFirst();}
    private Row row(UUID id){return db.query("SELECT * FROM billing_purchase WHERE id=?",this::map,id).stream().findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"Purchase not found"));}
    private Row map(ResultSet r,int index)throws SQLException{return new Row(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("provider"),r.getString("plan"),r.getString("provider_reference"),r.getString("provider_product"),r.getString("encrypted_token"),r.getString("status"),instant(r.getTimestamp("paid_through")),instant(r.getTimestamp("access_until")),r.getInt("verified_paid_count"),r.getString("cancellation_status"),instant(r.getTimestamp("cancel_at")),r.getObject("remaining_cycles",Integer.class),r.getBoolean("revoked"));}
    private Entry view(Row r){return new Entry(r.id,r.provider,r.plan,r.status,r.paidThrough,r.accessUntil,r.cancellation,r.provider.equals("RAZORPAY")?r.reference:null,r.provider.equals("GOOGLE_PLAY")?"https://play.google.com/store/account/subscriptions?package="+config.packageName+"&sku="+r.product:null);}
    private JsonNode parseJson(byte[] raw){try{JsonNode value=json.readTree(raw);if(value==null||!value.isObject())throw new IllegalArgumentException();return value;}catch(RuntimeException ex){throw error(HttpStatus.BAD_REQUEST,"Invalid provider notification");}}
    private void requireEventId(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,160}"))throw error(HttpStatus.BAD_REQUEST,"Invalid event ID");}
    private boolean seen(String provider,String id){return db.queryForObject("SELECT count(*) FROM billing_event WHERE provider=? AND event_id=?",Integer.class,provider,id)>0;}
    private void recordEvent(String provider,String id,UUID purchase,String type){db.update("INSERT INTO billing_event(provider,event_id,purchase_id,event_type) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",provider,id,purchase,type.length()>80?type.substring(0,80):type);}
    private Instant parseInstant(String value){try{return value.isBlank()?null:Instant.parse(value);}catch(RuntimeException ex){throw error(HttpStatus.BAD_GATEWAY,"Invalid provider period");}}
    private Instant epoch(long value){return value>0?Instant.ofEpochSecond(value):null;}private Instant instant(Timestamp v){return v==null?null:v.toInstant();}private Timestamp ts(Instant v){return v==null?null:Timestamp.from(v);}
    private boolean terminal(String status){return Set.of("CANCELED","EXPIRED","REVOKED").contains(status);}private void requireRazorpay(){if(!config.razorpayEnabled)throw error(HttpStatus.SERVICE_UNAVAILABLE,"Website billing is disabled");}private void requirePlay(){if(!config.playEnabled)throw error(HttpStatus.SERVICE_UNAVAILABLE,"Play billing is disabled");}
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
