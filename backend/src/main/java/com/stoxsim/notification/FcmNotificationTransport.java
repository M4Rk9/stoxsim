package com.stoxsim.notification;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FcmNotificationTransport implements NotificationTransport {
    private final JdbcTemplate db;private final FcmClient client;
    public FcmNotificationTransport(JdbcTemplate db,FcmClient client){this.db=db;this.client=client;}
    public String channel(){return "PUSH";}
    public boolean configured(){return client.configured();}
    public Result deliver(Message message) {
        if(!configured())return Result.PERMANENT_REJECTION;
        record Device(String hash,String token) {}
        var devices=db.query("SELECT token_hash,token FROM notification_device WHERE user_id=? ORDER BY updated_at DESC LIMIT 5",
            (r,n)->new Device(r.getString(1),r.getString(2)),message.userId());
        var outcomes=new ArrayList<Result>();
        for(var device:devices) {
            // Revalidate each installation immediately before sending; a switched installation
            // also validates the owner in the data-only payload before displaying generic text.
            if(!Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM notification_device d JOIN notification_settings s ON s.user_id=d.user_id
                  JOIN user_notification n ON n.user_id=d.user_id WHERE d.token_hash=? AND d.user_id=? AND d.token=?
                  AND s.push_enabled=true AND n.id=?)
                """,Boolean.class,device.hash(),message.userId(),device.token(),message.notificationId())))continue;
            var outcome=client.send(device.token(),message.userId(),message.notificationId());outcomes.add(outcome.result());
            if(outcome.unregistered())db.update("DELETE FROM notification_device WHERE token_hash=? AND user_id=? AND token=?",
                device.hash(),message.userId(),device.token());
        }
        return aggregate(outcomes);
    }
    static Result aggregate(List<Result> outcomes) {
        if(outcomes.isEmpty())return Result.PERMANENT_REJECTION;
        if(outcomes.contains(Result.UNKNOWN))return Result.UNKNOWN;
        if(outcomes.contains(Result.ACCEPTED))
            return outcomes.contains(Result.RETRYABLE_REJECTION)?Result.UNKNOWN:Result.ACCEPTED;
        return outcomes.contains(Result.RETRYABLE_REJECTION)?Result.RETRYABLE_REJECTION:Result.PERMANENT_REJECTION;
    }
}
