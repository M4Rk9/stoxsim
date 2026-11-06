package com.stoxsim.notification;
import java.util.UUID;
/** Retry only a definite rejection; an ambiguous network outcome must not be replayed. */
public interface NotificationTransport {
 enum Result { ACCEPTED, RETRYABLE_REJECTION, PERMANENT_REJECTION, UNKNOWN }
 record Message(UUID notificationId,UUID userId,String email,String title,String body,String href,String unsubscribeToken) {}
 String channel(); boolean configured(); Result deliver(Message message);
}
