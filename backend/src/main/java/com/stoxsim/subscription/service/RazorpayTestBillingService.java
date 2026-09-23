package com.stoxsim.subscription.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.subscription.provider.RazorpayTestClient;
import com.stoxsim.subscription.provider.RazorpayTestConfig;
import com.stoxsim.subscription.provider.RazorpaySignature;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Test ledger with explicitly opted-in, administrator-only sandbox benefits.
 */
@Service
public class RazorpayTestBillingService {
    public record Entry(UUID id,String plan,String providerId,String status,Instant currentPeriodEnd,int paidCount,
        boolean benefitsEnabled,String benefitStatus,Instant accessUntil) {}
    public record Overview(boolean enabled,String mode,String keyId,List<Entry> entries) {}
    private static final Set<String> STATES=Set.of("created","authenticated","active","pending","halted","cancelled","completed","expired","paused");
    private static final Set<String> EVENTS=Set.of("subscription.authenticated","subscription.activated","subscription.charged",
        "subscription.pending","subscription.halted","subscription.cancelled","subscription.completed",
        "subscription.paused","subscription.resumed","subscription.updated");
    private final JdbcTemplate db;
    private final AppUserRepository users;
    private final RazorpayTestConfig config;
    private final RazorpayTestClient provider;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final TestBenefitService benefits;
    public RazorpayTestBillingService(JdbcTemplate db,AppUserRepository users,RazorpayTestConfig config,
        RazorpayTestClient provider,ObjectMapper json,PlatformTransactionManager manager,TestBenefitService benefits) {
        this.db=db;this.users=users;this.config=config;this.provider=provider;this.json=json;
        tx=new TransactionTemplate(manager);tx.setTimeout(25);
        this.benefits=benefits;
    }
    private void admin(UUID actor) {
        var user=users.findById(actor).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Sign in again"));
        if(!user.isPlatformAdmin()) throw error(HttpStatus.FORBIDDEN,"Administrator access required for test billing");
        if(!user.isEmailVerified()) throw error(HttpStatus.FORBIDDEN,"Verify your email before testing billing");
    }
    private void enabled() {
        if(!config.enabled) throw error(HttpStatus.SERVICE_UNAVAILABLE,"Test billing is not configured");
    }
    public Overview overview(UUID actor) {
        admin(actor);
        return new Overview(config.enabled,"TEST",config.enabled?config.keyId:null,
            db.query("SELECT * FROM razorpay_test_subscription WHERE user_id=? ORDER BY created_at DESC LIMIT 20",this::mapEntry,actor));
    }
    public Entry create(UUID actor,String plan,UUID key) {
        admin(actor);enabled();
        if(!Set.of("PLUS","PRO").contains(plan)) throw error(HttpStatus.BAD_REQUEST,"Choose Plus or Pro");
        provider.validatePlan(plan);
        // Commit a reservation BEFORE any external creation. A timeout cannot cause
        // an automatic duplicate subscription. Recovery uses the opaque reference.
        record Reservation(Entry entry,boolean fresh) {}
        Reservation reservation=tx.execute(status->{
            users.findByIdForUpdate(actor).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Sign in again"));
            admin(actor);
            var prior=db.query("SELECT * FROM razorpay_test_subscription WHERE user_id=? AND request_key=?",this::mapEntry,actor,key);
            if(!prior.isEmpty()) {
                if(!prior.getFirst().plan().equals(plan)) throw error(HttpStatus.CONFLICT,"Retry key belongs to another plan");
                return new Reservation(prior.getFirst(),false);
            }
            Integer open=db.queryForObject("SELECT count(*) FROM razorpay_test_subscription WHERE user_id=? AND status NOT IN ('cancelled','completed','expired')",Integer.class,actor);
            if(open>0) throw error(HttpStatus.CONFLICT,"Finish or cancel the existing test subscription first");
            UUID id=UUID.randomUUID();
            db.update("INSERT INTO razorpay_test_subscription(id,user_id,request_key,plan,provider_plan_id) VALUES (?,?,?,?,?)",id,actor,key,plan,config.planId(plan));
            return new Reservation(entry(id),true);
        });
        if(!reservation.fresh()) return reservation.entry();
        var created=provider.create(plan,reservation.entry().id());
        return tx.execute(status->{
            lock(reservation.entry().id());
            // A webhook may already have reconciled the creation. Fetch current
            // state instead of overwriting it with the older creation response.
            var current=entry(reservation.entry().id());
            return apply(current,provider.fetch(created.path("id").asText()));
        });
    }
    public Entry refresh(UUID actor,UUID id) {
        admin(actor);enabled();
        return tx.execute(status->{
            owned(actor,id);var item=entry(id);
            if(item.providerId()==null) throw error(HttpStatus.CONFLICT,"Creation is awaiting confirmation. Keep this reference for reconciliation: "+id);
            return apply(item,provider.fetch(item.providerId()));
        });
    }
    public Entry reconcile(UUID actor,UUID id,String providerId) {
        admin(actor);enabled();
        return tx.execute(status->{
            owned(actor,id);
            return apply(entry(id),provider.fetch(providerId));
        });
    }
    public Entry benefits(UUID actor,UUID id,boolean enable) {
        admin(actor);
        if (enable) enabled();
        return tx.execute(status->{
            owned(actor,id);
            var item=entry(id);
            if (enable) {
                if (item.providerId()==null) throw error(HttpStatus.CONFLICT,"Confirm checkout first");
                item=apply(item,provider.fetch(item.providerId()));
                if (!item.status().equals("active") || item.paidCount()<1)
                    throw error(HttpStatus.CONFLICT,"Complete a successful test payment first");
                db.update("UPDATE razorpay_test_subscription SET benefits_enabled=false,benefit_status='OFF',access_until=NULL WHERE user_id=? AND id<>?",actor,id);
            }
            db.update("UPDATE razorpay_test_subscription SET benefits_enabled=? WHERE id=?",enable,id);
            benefits.sync(id);
            return entry(id);
        });
    }
    /** Bounded polling catches missed webhooks; one failed provider fetch does not stop other users. */
    public void reconcileBenefits() {
        var ids=db.query("SELECT id FROM razorpay_test_subscription WHERE benefits_enabled=true ORDER BY updated_at LIMIT 20",
            (rs,row)->rs.getObject(1,UUID.class));
        for (var id:ids) {
            try {
                tx.executeWithoutResult(status->{
                    lock(id);var item=entry(id);
                    if (config.enabled && item.providerId()!=null) apply(item,provider.fetch(item.providerId()));
                    else benefits.sync(id);
                    db.update("UPDATE razorpay_test_subscription SET updated_at=now() WHERE id=?",id);
                });
            } catch (RuntimeException failure) {
                // Expire locally even during a provider outage. Do not log provider bodies or credentials.
                try { tx.executeWithoutResult(status->{
                    lock(id);benefits.sync(id);
                    db.update("UPDATE razorpay_test_subscription SET updated_at=now() WHERE id=?",id);
                }); } catch (RuntimeException ignored) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Test billing reconciliation failed for local reference {}",id);
                }
            }
        }
    }
    public Entry cancel(UUID actor,UUID id) {
        admin(actor);enabled();
        return tx.execute(status->{
            owned(actor,id);var item=entry(id);
            if(item.providerId()==null) throw error(HttpStatus.CONFLICT,"Wait for creation confirmation before cancellation");
            var latest=apply(item,provider.fetch(item.providerId()));
            if(Set.of("cancelled","completed","expired").contains(latest.status())) return latest;
            provider.cancel(item.providerId());
            return apply(latest,provider.fetch(item.providerId()));
        });
    }
    public void webhook(byte[] raw,String signature,String eventId) {
        enabled();
        if(!RazorpaySignature.valid(raw,signature,config.webhookSecret))
            throw error(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");
        if(eventId==null || !eventId.matches("[A-Za-z0-9_-]{1,128}"))
            throw error(HttpStatus.BAD_REQUEST,"Missing or invalid event ID");
        JsonNode event;
        try { event=json.readTree(raw); } catch(Exception ex) { throw error(HttpStatus.BAD_REQUEST,"Invalid webhook JSON"); }
        if(event==null || !event.isObject()) throw error(HttpStatus.BAD_REQUEST,"Invalid webhook JSON");
        String type=event.path("event").asText();
        if(!EVENTS.contains(type)) return;
        var subscription=event.path("payload").path("subscription").path("entity");
        String providerId=subscription.path("id").asText();
        if(!providerId.matches("sub_[A-Za-z0-9]+")) throw error(HttpStatus.BAD_REQUEST,"Missing subscription");
        var ids=db.query("SELECT id FROM razorpay_test_subscription WHERE provider_id=?",(rs,row)->rs.getObject(1,UUID.class),providerId);
        if(ids.isEmpty()) {
            try {
                UUID reference=UUID.fromString(subscription.path("notes").path("stoxsim_test_reference").asText());
                ids=db.query("SELECT id FROM razorpay_test_subscription WHERE id=?",(rs,row)->rs.getObject(1,UUID.class),reference);
            } catch(IllegalArgumentException ex) { return; }
        }
        if(ids.isEmpty()) return; // Other applications in the merchant account.
        UUID id=ids.getFirst();
        tx.executeWithoutResult(status->{
            lock(id);
            if(db.queryForObject("SELECT count(*) FROM razorpay_test_event WHERE event_id=?",Integer.class,eventId)>0) return;
            // Event timestamps alone cannot order concurrent provider transitions.
            // Fetch the authoritative resource using TEST credentials under row lock.
            apply(entry(id),provider.fetch(providerId));
            db.update("INSERT INTO razorpay_test_event(event_id,subscription_id,event_type) VALUES (?,?,?) ON CONFLICT DO NOTHING",eventId,id,type);
        });
    }
    private Entry apply(Entry item,JsonNode remote) {
        String providerId=remote.path("id").asText(),state=remote.path("status").asText();
        String expected=db.queryForObject("SELECT provider_plan_id FROM razorpay_test_subscription WHERE id=?",String.class,item.id());
        if(!providerId.matches("sub_[A-Za-z0-9]+") || (item.providerId()!=null && !item.providerId().equals(providerId))
            || !remote.path("plan_id").asText().equals(expected)
            || !remote.path("notes").path("stoxsim_test_reference").asText().equals(item.id().toString())
            || remote.path("quantity").asInt()!=1 || !STATES.contains(state))
            throw error(HttpStatus.BAD_GATEWAY,"Provider subscription does not match the test checkout");
        long end=remote.path("current_end").asLong(0);
        db.update("UPDATE razorpay_test_subscription SET provider_id=?,status=?,current_period_end=?,paid_count=?,updated_at=now() WHERE id=?",
            providerId,state,end>0?Timestamp.from(Instant.ofEpochSecond(end)):null,remote.path("paid_count").asInt(0),item.id());
        // Only a newly confirmed paid cycle may advance the paid-through boundary.
        // Pending events and retries cannot extend the fixed three-day grace period.
        if (state.equals("active") && end>0 && remote.path("paid_count").asInt(0)>0)
            db.update("UPDATE razorpay_test_subscription SET paid_through=?,verified_paid_count=? WHERE id=? AND verified_paid_count<?",
                Timestamp.from(Instant.ofEpochSecond(end)),remote.path("paid_count").asInt(),item.id(),remote.path("paid_count").asInt());
        benefits.sync(item.id());
        return entry(item.id());
    }
    private void owned(UUID actor,UUID id) {
        lock(id);
        UUID owner=db.queryForObject("SELECT user_id FROM razorpay_test_subscription WHERE id=?",UUID.class,id);
        if(!actor.equals(owner)) throw error(HttpStatus.NOT_FOUND,"Test subscription not found");
        admin(actor);
    }
    private void lock(UUID id) {
        var owners=db.query("SELECT user_id FROM razorpay_test_subscription WHERE id=?",(rs,row)->rs.getObject(1,UUID.class),id);
        if(owners.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Test subscription not found");
        users.findByIdForUpdate(owners.getFirst()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Test subscription not found"));
        var rows=db.query("SELECT id FROM razorpay_test_subscription WHERE id=? FOR UPDATE",(rs,row)->rs.getObject(1,UUID.class),id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Test subscription not found");
    }
    private Entry entry(UUID id) {
        return db.query("SELECT * FROM razorpay_test_subscription WHERE id=?",this::mapEntry,id).stream()
            .findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"Test subscription not found"));
    }
    private Entry mapEntry(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        var end=rs.getTimestamp("current_period_end");
        var until=rs.getTimestamp("access_until");
        return new Entry(rs.getObject("id",UUID.class),rs.getString("plan"),rs.getString("provider_id"),
            rs.getString("status"),end==null?null:end.toInstant(),rs.getInt("paid_count"),
            rs.getBoolean("benefits_enabled"),rs.getString("benefit_status"),until==null?null:until.toInstant());
    }
    private ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
