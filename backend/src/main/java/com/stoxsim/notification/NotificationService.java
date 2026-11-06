package com.stoxsim.notification;

import static com.stoxsim.notification.NotificationDtos.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class NotificationService {
 private final JdbcTemplate db;
 private final Clock clock;
 private final MeterRegistry meters;
 public NotificationService(JdbcTemplate db,Clock clock,MeterRegistry meters) {this.db=db;this.clock=clock;this.meters=meters;}
 @Transactional(readOnly=true)
 public Inbox inbox(UUID user) {
  requireUser(user,false);
  var items=db.query("SELECT * FROM user_notification WHERE user_id=? ORDER BY created_at DESC,id LIMIT 50",this::item,user);
  return new Inbox(items,db.queryForObject("SELECT count(*) FROM user_notification WHERE user_id=? AND read_at IS NULL",Long.class,user));
 }
 @Transactional(readOnly=true)
 public Preferences preferences(UUID user) {
  requireUser(user,false);
  return db.query("SELECT * FROM notification_settings WHERE user_id=?",(r,n)->new Preferences(r.getBoolean("learning"),r.getBoolean("competitions"),
   r.getBoolean("portfolio_education"),r.getBoolean("product_announcements"),r.getBoolean("promotional_messages"),r.getBoolean("push_enabled"),r.getBoolean("email_digest")),user)
   .stream().findFirst().orElse(Preferences.defaults());
 }
 @Transactional
 public Preferences updatePreferences(UUID user,Preferences p) {
  requireUser(user,true);
  if(p.emailDigest() && !Boolean.TRUE.equals(db.queryForObject("SELECT email_verified_at IS NOT NULL FROM app_user WHERE id=?",Boolean.class,user)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Verify your email before enabling notification emails");
  db.update("""
   INSERT INTO notification_settings(user_id,learning,competitions,portfolio_education,product_announcements,promotional_messages,push_enabled,email_digest,updated_at)
   VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET learning=excluded.learning,competitions=excluded.competitions,
   portfolio_education=excluded.portfolio_education,product_announcements=excluded.product_announcements,
   promotional_messages=excluded.promotional_messages,push_enabled=excluded.push_enabled,email_digest=excluded.email_digest,updated_at=excluded.updated_at
   """,user,p.learning(),p.competitions(),p.portfolioEducation(),p.productAnnouncements(),p.promotionalMessages(),p.pushEnabled(),p.emailDigest(),Timestamp.from(clock.instant()));
  if(p.emailDigest()) db.update("DELETE FROM notification_suppression WHERE user_id=? AND reason='UNSUBSCRIBED'",user);
  if(!p.pushEnabled()) removeAllDevices(user);
  db.update("""
   UPDATE notification_outbox o SET status='CANCELLED',completed_at=? FROM user_notification n
   WHERE o.notification_id=n.id AND n.user_id=? AND o.status='PENDING'
   AND ((o.channel='EMAIL' AND ?=FALSE) OR (o.channel='PUSH' AND ?=FALSE))
   """,Timestamp.from(clock.instant()),user,p.emailDigest(),p.pushEnabled());
  return p;
 }
 @Transactional public void markRead(UUID user,UUID id) {
  requireUser(user,false);
  if(db.update("UPDATE user_notification SET read_at=coalesce(read_at,?) WHERE id=? AND user_id=?",Timestamp.from(clock.instant()),id,user)==0)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found");
 }
 @Transactional public void markAllRead(UUID user) {
  requireUser(user,false);db.update("UPDATE user_notification SET read_at=? WHERE user_id=? AND read_at IS NULL",Timestamp.from(clock.instant()),user);
 }
 @Transactional public void registerDevice(UUID user,Device device) {
  requireUser(user,true);
  if(!preferences(user).pushEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Enable push notifications before registering a device");
  // An authenticated installation may move its opaque token to its newly signed-in account.
  db.update("""
   INSERT INTO notification_device(token_hash,user_id,token,platform,updated_at) VALUES(?,?,?,?,?)
   ON CONFLICT(token_hash) DO UPDATE SET user_id=excluded.user_id,token=excluded.token,updated_at=excluded.updated_at
   """,hash(device.token()),user,device.token(),device.platform(),Timestamp.from(clock.instant()));
  db.update("DELETE FROM notification_device WHERE user_id=? AND token_hash IN (SELECT token_hash FROM notification_device WHERE user_id=? ORDER BY updated_at DESC,token_hash OFFSET 5)",user,user);
 }
 @Transactional public void removeDevice(UUID user,String token) {requireUser(user,false);db.update("DELETE FROM notification_device WHERE token_hash=? AND user_id=?",hash(token),user);}
 @Transactional public void removeAllDevices(UUID user) {db.update("DELETE FROM notification_device WHERE user_id=?",user);}
 @Transactional public UUID publish(NotificationRequested event) {
  validate(event);requireUser(event.userId(),true);
  if(db.update("INSERT INTO notification_source_seen(user_id,event_key,observed_at) VALUES(?,?,?) ON CONFLICT DO NOTHING",event.userId(),event.eventKey(),Timestamp.from(clock.instant()))==0) return null;
  var p=preferences(event.userId());if(!p.allows(event.category(),event.promotional())) return null;
  boolean optional=event.category()!=NotificationCategory.SECURITY && event.category()!=NotificationCategory.BILLING;
  long count=db.queryForObject("SELECT count(*) FROM user_notification WHERE user_id=? AND created_at>? AND category NOT IN ('SECURITY','BILLING')",Long.class,event.userId(),Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
  if(optional && count>=10) {meters.counter("stoxsim.notifications.suppressed","reason","daily_limit").increment();return null;}
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO user_notification(id,user_id,event_key,category,title,body,href,promotional,created_at) VALUES(?,?,?,?,?,?,?,?,?)",id,event.userId(),event.eventKey(),event.category().name(),event.title(),event.body(),event.href(),event.promotional(),Timestamp.from(clock.instant()));
  if(p.emailDigest()) queue(id,"EMAIL");if(p.pushEnabled()) queue(id,"PUSH");
  meters.counter("stoxsim.notifications.created","category",event.category().name()).increment();return id;
 }
 private void queue(UUID id,String channel) {
  db.update("INSERT INTO notification_outbox(id,notification_id,channel,available_at) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",UUID.randomUUID(),id,channel,Timestamp.from(clock.instant()));
 }
 @Transactional public String unsubscribeToken(UUID user) {
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  db.update("INSERT INTO notification_unsubscribe(token_hash,user_id,expires_at) VALUES(?,?,?)",hash(token),user,Timestamp.from(clock.instant().plus(Duration.ofDays(90))));return token;
 }
 @Transactional public void unsubscribe(String token) {
  if(token==null || token.length()<32 || token.length()>128) return;
  db.queryForList("SELECT user_id FROM notification_unsubscribe WHERE token_hash=? AND expires_at>?",UUID.class,hash(token),Timestamp.from(clock.instant())).forEach(user->suppress(user,"UNSUBSCRIBED"));
 }
 @Transactional public void suppress(UUID user,String reason) {
  if(!List.of("UNSUBSCRIBED","BOUNCE","COMPLAINT").contains(reason)) throw new IllegalArgumentException("Invalid suppression reason");
  requireUser(user,true);
  db.update("""
   INSERT INTO notification_suppression(user_id,reason,created_at) VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE
   SET reason=CASE WHEN notification_suppression.reason IN ('BOUNCE','COMPLAINT') THEN notification_suppression.reason ELSE excluded.reason END,created_at=excluded.created_at
   """,user,reason,Timestamp.from(clock.instant()));
  db.update("UPDATE notification_settings SET email_digest=FALSE WHERE user_id=?",user);
  db.update("UPDATE notification_outbox o SET status='CANCELLED' FROM user_notification n WHERE n.id=o.notification_id AND n.user_id=? AND o.channel='EMAIL' AND o.status='PENDING'",user);
 }
 @Transactional public void administratorSuppress(UUID admin,UUID user,String reason) {
  requireUser(admin,false);
  if(!"ADMIN".equals(db.queryForObject("SELECT platform_role FROM app_user WHERE id=?",String.class,admin))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform administrator access is required");
  suppress(user,reason);
 }
 public void requireUser(UUID user,boolean lock) {
  if(db.queryForList("SELECT id FROM app_user WHERE id=?"+(lock?" FOR UPDATE":""),UUID.class,user).isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User no longer exists");
 }
 static void validate(NotificationRequested e) {
  if(e.userId()==null || e.category()==null || (e.promotional() && e.category()!=NotificationCategory.PRODUCT) || e.eventKey()==null || e.eventKey().isBlank() || e.eventKey().length()>160
   || e.title()==null || e.title().isBlank() || e.title().length()>160 || e.body()==null || e.body().length()>1000
   || e.href()==null || e.href().length()>300 || !e.href().matches("/(?:[A-Za-z0-9_-]+/?)*(?:#[A-Za-z0-9_-]+)?")) throw new IllegalArgumentException("Invalid notification content or destination");
 }
 public static String hash(String token) {
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private Item item(ResultSet r,int row) throws SQLException {
  var read=r.getTimestamp("read_at");return new Item(r.getObject("id",UUID.class),NotificationCategory.valueOf(r.getString("category")),r.getString("title"),r.getString("body"),r.getString("href"),r.getTimestamp("created_at").toInstant(),read==null?null:read.toInstant());
 }
}
