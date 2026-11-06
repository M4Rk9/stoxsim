package com.stoxsim.notification;
import java.time.Clock;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

/** Reads committed events; trading never waits for optional delivery. */
@Component @ConditionalOnProperty(name="stoxsim.notifications.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class NotificationScheduler {
 private final JdbcTemplate db;
 private final NotificationTransactions transactions;
 private final NotificationOutbox outbox;
 private final Clock clock;
 public NotificationScheduler(JdbcTemplate db,NotificationTransactions transactions,NotificationOutbox outbox,Clock clock){this.db=db;this.transactions=transactions;this.outbox=outbox;this.clock=clock;}
 @Scheduled(initialDelayString="${stoxsim.notifications.initial-delay-ms:60000}",fixedDelayString="${stoxsim.notifications.poll-delay-ms:60000}")
 public void poll(){try{materialize();outbox.dispatch();}catch(RuntimeException e){LoggerFactory.getLogger(getClass()).warn("Optional notification job failed ({})",e.getClass().getSimpleName());}}
 public void materialize(){
  var since=db.queryForObject("SELECT greatest(started_at,?) FROM notification_observation",Timestamp.class,Timestamp.from(clock.instant().minus(Duration.ofDays(90))));
  var events=db.query("""
   SELECT * FROM (
    SELECT user_id,'achievement:'||id event_key,'LEARNING' category,'Learning achievement unlocked' title,
     'Your learning progress has unlocked an achievement. Review it when you are ready.' body,'/progress' href,unlocked_at happened
    FROM achievement_unlock WHERE unlocked_at>=?
    UNION ALL SELECT user_id,'weekly:'||id,'PORTFOLIO_EDUCATION','Your weekly learning report is available',
     'Review your simulated portfolio activity and choose one thing to learn next.','/settings',generated_at
    FROM weekly_portfolio_report WHERE generated_at>=?
    UNION ALL SELECT e.user_id,'competition-ending:'||e.season_id,'COMPETITION','Your competition ends soon',
     'An enrolled competition ends within 24 hours. Review your learning; additional trades are not required.','/competitions',s.ends_at
    FROM competition_entry e JOIN competition_season s ON s.id=e.season_id WHERE s.ends_at>? AND s.ends_at<=?
    UNION ALL SELECT user_id,'security:'||id,'SECURITY','Account security update',
     'Your account security settings or sessions changed. Review recent security events if this was unexpected.','/settings',created_at
    FROM account_event WHERE user_id IS NOT NULL AND created_at>=?
     AND event_type IN ('PASSWORD_CHANGED','PASSWORD_ADDED','PASSWORD_RESET','GOOGLE_ACCOUNT_LINKED','GOOGLE_ACCOUNT_UNLINKED','SESSION_REVOKED','ALL_SESSIONS_REVOKED')
   ) e WHERE NOT EXISTS(SELECT 1 FROM notification_source_seen s WHERE s.user_id=e.user_id AND s.event_key=e.event_key)
   ORDER BY happened,user_id,event_key LIMIT 100
   """,(r,n)->new NotificationRequested(r.getObject("user_id",UUID.class),NotificationCategory.valueOf(r.getString("category")),r.getString("event_key"),r.getString("title"),r.getString("body"),r.getString("href")),
   since,since,Timestamp.from(clock.instant()),Timestamp.from(clock.instant().plus(Duration.ofHours(24))),since);
  for(var event:events){try{transactions.publish(event);}catch(RuntimeException e){LoggerFactory.getLogger(getClass()).warn("Notification event postponed ({})",e.getClass().getSimpleName());}}
 }
 @Scheduled(cron="0 31 3 * * *",zone="UTC") public void retention(){
  var before=Timestamp.from(clock.instant().minus(Duration.ofDays(90)));
  db.update("DELETE FROM user_notification WHERE id IN(SELECT id FROM user_notification WHERE created_at<? LIMIT 1000)",before);
  db.update("DELETE FROM notification_source_seen WHERE(user_id,event_key) IN(SELECT user_id,event_key FROM notification_source_seen WHERE observed_at<? LIMIT 1000)",Timestamp.from(clock.instant().minus(Duration.ofDays(120))));
  db.update("DELETE FROM notification_device WHERE token_hash IN(SELECT token_hash FROM notification_device WHERE updated_at<? LIMIT 1000)",before);
  db.update("DELETE FROM notification_unsubscribe WHERE token_hash IN(SELECT token_hash FROM notification_unsubscribe WHERE expires_at<? LIMIT 1000)",Timestamp.from(clock.instant()));
 }
}
