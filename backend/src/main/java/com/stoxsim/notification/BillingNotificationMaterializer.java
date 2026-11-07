package com.stoxsim.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Observe committed billing snapshots, without participating in provider verification or changing entitlements. */
@Service
public class BillingNotificationMaterializer {
    // This fingerprint is a deduplication key, not an authentication hash. Provider tokens/references are excluded.
    private static final String SNAPSHOT = """
        SELECT p.id,p.user_id,p.status,p.created_at,p.cancellation_status,p.revoked,
          coalesce((SELECT sum(c.refunded_minor) FROM billing_charge c WHERE c.purchase_id=p.id),0) refunded,
          'billing-state:'||p.id||':'||md5(jsonb_build_array(p.status,extract(epoch FROM p.paid_through),
            p.cancellation_status,p.revoked,
            coalesce((SELECT sum(c.refunded_minor) FROM billing_charge c WHERE c.purchase_id=p.id),0))::text) event_key
        FROM billing_purchase p
        """;
    private final JdbcTemplate db;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate tx;

    public BillingNotificationMaterializer(JdbcTemplate db,NotificationService notifications,Clock clock,PlatformTransactionManager manager) {
        this.db=db;this.notifications=notifications;this.clock=clock;this.tx=new TransactionTemplate(manager);
    }

    public void materialize() {
        var candidates=db.query("""
            SELECT snapshot.id,snapshot.user_id FROM (%s) snapshot
            WHERE NOT EXISTS(SELECT 1 FROM notification_source_seen s WHERE s.user_id=snapshot.user_id AND s.event_key=snapshot.event_key)
            ORDER BY snapshot.created_at,snapshot.id LIMIT 100
            """.formatted(SNAPSHOT),(r,n)->new Candidate(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class)));
        for(var candidate:candidates) {
            tx.executeWithoutResult(ignored->observe(candidate));
        }
    }

    private void observe(Candidate candidate) {
        // Match the billing writer's lock order: owner first, then purchase. Re-read after locking, so stale candidates cannot send obsolete states.
        if(db.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",UUID.class,candidate.userId()).isEmpty())return;
        var rows=db.query(SNAPSHOT+" WHERE p.id=? AND p.user_id=? FOR UPDATE OF p",(r,n)->new Snapshot(
            r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("status"),r.getTimestamp("created_at").toInstant(),
            r.getString("cancellation_status"),r.getBoolean("revoked"),r.getLong("refunded"),r.getString("event_key")),candidate.id(),candidate.userId());
        if(rows.isEmpty())return;
        var state=rows.getFirst();
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM notification_source_seen WHERE user_id=? AND event_key=?)",Boolean.class,state.userId(),state.eventKey())))return;
        boolean first=db.update("INSERT INTO notification_source_seen(user_id,event_key,observed_at) VALUES(?,?,?) ON CONFLICT DO NOTHING",
            state.userId(),"billing-observed:"+state.id(),Timestamp.from(clock.instant()))==1;
        Instant boundary=db.queryForObject("SELECT greatest(started_at,?) FROM notification_observation",Timestamp.class,
            Timestamp.from(clock.instant().minus(Duration.ofDays(90)))).toInstant();
        String title=title(state);
        if((first&&state.createdAt().isBefore(boundary))||title==null) {
            // Never label an existing subscription as a newly observed activation. Preserve its baseline for subsequent real changes.
            db.update("INSERT INTO notification_source_seen(user_id,event_key,observed_at) VALUES(?,?,?) ON CONFLICT DO NOTHING",state.userId(),state.eventKey(),Timestamp.from(clock.instant()));
            return;
        }
        notifications.publish(new NotificationRequested(state.userId(),NotificationCategory.BILLING,state.eventKey(),title,
            "Your verified subscription record changed. Review billing settings for current access, renewal or cancellation details.","/settings"));
    }

    private String title(Snapshot state) {
        if(state.refunded()>0)return "Subscription refund recorded";
        if(state.revoked()||state.status().equals("REVOKED"))return "Subscription access revoked";
        if(state.cancellation().equals("REQUESTED"))return "Subscription cancellation requested";
        if(state.cancellation().equals("CONFIRMED")||state.status().equals("ENDING")||state.status().equals("CANCELED"))return "Subscription cancellation updated";
        return switch(state.status()) {
            case "ACTIVE"->"Subscription access confirmed"; // Applies both to activation and a verified new paid-through period.
            case "GRACE","PAST_DUE"->"Subscription payment needs attention";
            case "EXPIRED"->"Subscription access expired";
            default->null; // Creating/pending is not an authoritative payment success or failure.
        };
    }

    private record Candidate(UUID id,UUID userId) {}
    private record Snapshot(UUID id,UUID userId,String status,Instant createdAt,String cancellation,boolean revoked,long refunded,String eventKey) {}
}
