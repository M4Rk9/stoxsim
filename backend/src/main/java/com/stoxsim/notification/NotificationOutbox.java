package com.stoxsim.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class NotificationOutbox {
 private final JdbcTemplate db;
 private final NotificationService notifications;
 private final Clock clock;
 private final Map<String,NotificationTransport> transports;
 private final TransactionTemplate tx;
 private final boolean enabled;
 private final MeterRegistry meters;
 public NotificationOutbox(JdbcTemplate db,NotificationService notifications,Clock clock,List<NotificationTransport> transports,
  PlatformTransactionManager manager,@Value("${stoxsim.notifications.delivery-enabled:false}") boolean enabled,MeterRegistry meters) {
  this.db=db;this.notifications=notifications;this.clock=clock;
  for(var transport:transports) if(!List.of("EMAIL","PUSH").contains(transport.channel())) throw new IllegalArgumentException("Unknown notification transport");
  this.transports=transports.stream().collect(Collectors.toMap(NotificationTransport::channel,Function.identity()));
  this.tx=new TransactionTemplate(manager);this.enabled=enabled;this.meters=meters;
 }
 public void dispatch() {
  if(!enabled) return;
  db.update("UPDATE notification_outbox SET status='UNCERTAIN',completed_at=? WHERE status='PROCESSING' AND claimed_at<?",Timestamp.from(clock.instant()),Timestamp.from(clock.instant().minus(Duration.ofMinutes(5))));
  for(int i=0;i<20;i++) {
   Claim claim=tx.execute(s->claim());if(claim==null)return;
   if(!eligible(claim)){finish(claim,"CANCELLED");continue;}
   NotificationTransport.Result result;
   try {
    String token=claim.channel().equals("EMAIL")?notifications.unsubscribeToken(claim.userId()):null;
    result=transports.get(claim.channel()).deliver(new NotificationTransport.Message(claim.notificationId(),claim.userId(),claim.email(),claim.title(),claim.body(),claim.href(),token));
   } catch(RuntimeException error){result=NotificationTransport.Result.UNKNOWN;}
   switch(result) {
    case ACCEPTED->finish(claim,"SENT");case PERMANENT_REJECTION->finish(claim,"FAILED");case UNKNOWN->finish(claim,"UNCERTAIN");
    case RETRYABLE_REJECTION->{
     if(claim.attempts()>=3)finish(claim,"FAILED");
     else db.update("UPDATE notification_outbox SET status='PENDING',available_at=? WHERE id=? AND status='PROCESSING'",Timestamp.from(clock.instant().plus(Duration.ofMinutes(5L*claim.attempts()))),claim.id());
    }
   }
  }
 }
 private Claim claim() {
  var channels=transports.values().stream().filter(NotificationTransport::configured).map(NotificationTransport::channel).toList();
  if(channels.isEmpty())return null;
  String selection="""
   SELECT o.id,o.notification_id,o.channel,o.attempts,n.user_id,n.title,n.body,n.href,u.email
   FROM notification_outbox o JOIN user_notification n ON n.id=o.notification_id JOIN app_user u ON u.id=n.user_id
   WHERE o.status='PENDING' AND o.available_at<=? AND o.attempts<3 AND o.channel IN (%s)
   """.formatted(channels.stream().map(c->"'"+c+"'").collect(Collectors.joining(",")));
  org.springframework.jdbc.core.RowMapper<Claim> mapper=(r,n)->new Claim(r.getObject("id",UUID.class),r.getObject("notification_id",UUID.class),r.getString("channel"),r.getInt("attempts")+1,
     r.getObject("user_id",UUID.class),r.getString("email"),r.getString("title"),r.getString("body"),r.getString("href"));
  // Preferences and unsubscribe lock owner -> outbox. Candidate discovery holds no locks;
  // skip busy owners, then lock/revalidate the exact outbox row in the same order.
  var candidates=db.query(selection+" ORDER BY o.available_at,o.id LIMIT 20",mapper,Timestamp.from(clock.instant()));
  Claim c=null;
  for(var candidate:candidates) {
   if(db.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE SKIP LOCKED",UUID.class,candidate.userId()).isEmpty())continue;
   var ready=db.query(selection+" AND o.id=? AND n.user_id=? FOR UPDATE OF o SKIP LOCKED",mapper,Timestamp.from(clock.instant()),candidate.id(),candidate.userId());
   if(!ready.isEmpty()){c=ready.getFirst();break;}
  }
  if(c==null)return null;
  long recent=db.queryForObject("""
   SELECT count(*) FROM notification_outbox o JOIN user_notification n ON n.id=o.notification_id
   WHERE n.user_id=? AND o.channel=? AND o.status IN ('SENT','PROCESSING','UNCERTAIN') AND o.claimed_at>?
   """,Long.class,c.userId(),c.channel(),Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
  if(recent>=(c.channel().equals("EMAIL")?1:3)) {
   db.update("UPDATE notification_outbox SET status='CANCELLED',completed_at=? WHERE id=?",Timestamp.from(clock.instant()),c.id());
   meters.counter("stoxsim.notifications.delivery","channel",c.channel(),"result","daily_limit").increment();return c.cancelled();
  }
  db.update("UPDATE notification_outbox SET status='PROCESSING',attempts=attempts+1,claimed_at=? WHERE id=?",Timestamp.from(clock.instant()),c.id());return c;
 }
 private boolean eligible(Claim c) {
  if(c.attempts()<0)return false;
  return Boolean.TRUE.equals(tx.execute(s->{
   if(db.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",UUID.class,c.userId()).isEmpty())return false;
   var p=notifications.preferences(c.userId());
   var allowed=db.query("SELECT category,promotional FROM user_notification WHERE id=?",(r,n)->p.allows(NotificationCategory.valueOf(r.getString("category")),r.getBoolean("promotional")),c.notificationId());
   if(allowed.isEmpty()||!allowed.getFirst())return false;
   if(c.channel().equals("EMAIL"))return p.emailDigest()
    && Boolean.TRUE.equals(db.queryForObject("SELECT email_verified_at IS NOT NULL AND email=? FROM app_user WHERE id=?",Boolean.class,c.email(),c.userId()))
    && db.queryForObject("SELECT count(*) FROM notification_suppression WHERE user_id=?",Long.class,c.userId())==0;
   return p.pushEnabled()&&db.queryForObject("SELECT count(*) FROM notification_device WHERE user_id=?",Long.class,c.userId())>0;
  }));
 }
 private void finish(Claim c,String state) {
  db.update("UPDATE notification_outbox SET status=?,completed_at=? WHERE id=? AND status='PROCESSING'",state,Timestamp.from(clock.instant()),c.id());
  meters.counter("stoxsim.notifications.delivery","channel",c.channel(),"result",state.toLowerCase(java.util.Locale.ROOT)).increment();
 }
 record Claim(UUID id,UUID notificationId,String channel,int attempts,UUID userId,String email,String title,String body,String href) {
  Claim cancelled(){return new Claim(id,notificationId,channel,-1,userId,email,title,body,href);}
 }
}
