package com.stoxsim.notification;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.stoxsim.auth.config.AuthProperties;

@Component
public class SmtpNotificationTransport implements NotificationTransport {
 private final AuthProperties auth;
 private final boolean enabled;
 private final String frontend,unsubscribeBase;
 public SmtpNotificationTransport(AuthProperties auth,@Value("${stoxsim.notifications.email-enabled:false}") boolean enabled,
  @Value("${stoxsim.frontend-url}") String frontend,@Value("${stoxsim.notifications.unsubscribe-api-url:}") String unsubscribeBase) {
  this.auth=auth;this.enabled=enabled;this.frontend=frontend;this.unsubscribeBase=unsubscribeBase;
 }
 public String channel(){return "EMAIL";}
 public boolean configured(){return enabled&&auth.isMailStartTls()&&StringUtils.hasText(auth.getMailHost())&&safeHttps(frontend)&&safeHttps(unsubscribeBase)
  && URI.create(unsubscribeBase).getPath().equals("/api/v1/notifications/unsubscribe");}
 public Result deliver(Message m) {
  if(!configured())return Result.PERMANENT_REJECTION;
  var sender=new JavaMailSenderImpl();sender.setHost(auth.getMailHost());sender.setPort(auth.getMailPort());sender.setUsername(auth.getMailUsername());sender.setPassword(auth.getMailPassword());
  Properties p=sender.getJavaMailProperties();p.put("mail.smtp.auth",String.valueOf(StringUtils.hasText(auth.getMailUsername())));
  p.put("mail.smtp.starttls.enable","true");p.put("mail.smtp.starttls.required","true");p.put("mail.smtp.ssl.checkserveridentity","true");
  p.put("mail.smtp.connectiontimeout","5000");p.put("mail.smtp.timeout","5000");p.put("mail.smtp.writetimeout","5000");
  try {
   var mail=sender.createMimeMessage();var helper=new MimeMessageHelper(mail,false,StandardCharsets.UTF_8.name());
   helper.setFrom(auth.getMailFrom());helper.setTo(m.email());helper.setSubject("Your StoxSim learning updates");
   helper.setText(m.title()+"\n\n"+m.body()+"\n\nReview this and other updates: "+frontend+m.href()
    +"\n\nManage optional communications: "+frontend+"/settings#notifications"
    +"\nStop optional notification emails: "+frontend+"/notifications/unsubscribe?token="+m.unsubscribeToken()
    +"\n\nStoxSim is an educational simulator. You do not need to trade more often to keep learning.");
   mail.setHeader("List-Unsubscribe","<"+unsubscribeBase+"?token="+m.unsubscribeToken()+">");mail.setHeader("List-Unsubscribe-Post","List-Unsubscribe=One-Click");
   mail.setHeader("X-StoxSim-Notification",m.notificationId().toString());sender.send(mail);return Result.ACCEPTED;
  }catch(MailAuthenticationException e){return Result.PERMANENT_REJECTION;}
   catch(MailException|jakarta.mail.MessagingException e){return Result.UNKNOWN;}
 }
 static boolean safeHttps(String value){try{URI uri=URI.create(value);return "https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getRawQuery()==null&&uri.getRawFragment()==null;}catch(IllegalArgumentException e){return false;}}
}
