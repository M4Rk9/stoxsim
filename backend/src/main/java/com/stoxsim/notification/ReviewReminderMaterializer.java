package com.stoxsim.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded, generic inbox reminders. Never copies private strategy text or records user activity. */
@Service
public class ReviewReminderMaterializer {
    private static final String DUE = """
        reviewed_at IS NULL AND review_on BETWEEN ? AND ?
        AND review_reminded_on IS DISTINCT FROM review_on
        """;
    private final JdbcTemplate db;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ReviewReminderMaterializer(JdbcTemplate db,NotificationService notifications,Clock clock,PlatformTransactionManager manager) {
        this.db=db;this.notifications=notifications;this.clock=clock;this.tx=new TransactionTemplate(manager);
        this.tx.setTimeout(5);
    }

    public void materialize() {
        var today=LocalDate.ofInstant(clock.instant(),ZoneOffset.UTC);
        var since=today.minusDays(29);
        var owners=db.queryForList("SELECT user_id FROM decision_reflection WHERE "+DUE+
            " GROUP BY user_id ORDER BY min(review_on),user_id LIMIT 100",UUID.class,since,today);
        for(var owner:owners) {
            try { tx.executeWithoutResult(ignored->observe(owner,since,today)); }
            catch(RuntimeException e) {
                LoggerFactory.getLogger(getClass()).warn("Journal reminder postponed ({})",e.getClass().getSimpleName());
            }
        }
    }

    private void observe(UUID owner,LocalDate since,LocalDate today) {
        // Match notification preference/account-deletion serialization. Skip active journal edits
        // rather than waiting on a reflection lock while holding its owner's lock.
        if(db.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE SKIP LOCKED",UUID.class,owner).isEmpty())return;
        var notes=db.queryForList("SELECT id FROM decision_reflection WHERE user_id=? AND "+DUE+
            " ORDER BY review_on,id LIMIT 1000 FOR UPDATE SKIP LOCKED",UUID.class,owner,since,today);
        if(notes.isEmpty())return;
        notifications.publishInboxOnly(new NotificationRequested(owner,NotificationCategory.PORTFOLIO_EDUCATION,
            "journal-review:"+today,"Time to review your strategy",
            "You have a scheduled review to revisit. Compare your thesis with what happened and what you learned; profit alone does not measure a good decision.","/journal"));
        // Consume suppressed/daily-capped events as well: opting back in must not replay old nudges.
        // The watermark survives notification retention; editing to a different date can schedule a new reminder.
        for(var note:notes)db.update("UPDATE decision_reflection SET review_reminded_on=review_on WHERE id=? AND user_id=?",note,owner);
    }
}
