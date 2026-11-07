package com.stoxsim.notification;
import java.util.UUID;
/** Publish in the authoritative transaction; the listener runs only after it commits. */
public record NotificationRequested(UUID userId, NotificationCategory category, String eventKey, String title, String body, String href, boolean promotional) {
 public NotificationRequested(UUID userId,NotificationCategory category,String eventKey,String title,String body,String href) {
  this(userId,category,eventKey,title,body,href,false);
 }
}
