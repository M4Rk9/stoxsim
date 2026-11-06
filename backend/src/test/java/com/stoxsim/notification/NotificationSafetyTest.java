package com.stoxsim.notification;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationSafetyTest {
 @Test void unsafeLinksAndOversizedContentAreRejected() {
  for(String href:new String[]{"https://attacker.test","//attacker.test","/\\evil","/%2f%2fevil","/settings?token=secret","javascript:alert(1)"})
   assertThatThrownBy(()->NotificationService.validate(new NotificationRequested(UUID.randomUUID(),NotificationCategory.LEARNING,"key","Title","Body",href))).isInstanceOf(IllegalArgumentException.class);
  NotificationService.validate(new NotificationRequested(UUID.randomUUID(),NotificationCategory.LEARNING,"key","Title","Body","/settings#notifications"));
  assertThatThrownBy(()->NotificationService.validate(new NotificationRequested(UUID.randomUUID(),NotificationCategory.LEARNING,"key","Title","x".repeat(1001),"/progress"))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void defaultsNeverOptInToExternalOrPromotionalMessages() {
  var p=NotificationDtos.Preferences.defaults();assertThat(p.emailDigest()).isFalse();assertThat(p.pushEnabled()).isFalse();
  assertThat(p.productAnnouncements()).isFalse();assertThat(p.promotionalMessages()).isFalse();
  assertThat(p.allows(NotificationCategory.SECURITY)).isTrue();assertThat(p.allows(NotificationCategory.BILLING)).isTrue();
  var announcementsOnly=new NotificationDtos.Preferences(true,true,true,true,false,false,false);
  assertThat(announcementsOnly.allows(NotificationCategory.PRODUCT,false)).isTrue();assertThat(announcementsOnly.allows(NotificationCategory.PRODUCT,true)).isFalse();
 }
 @Test void emailLinksRequireUnambiguousHttps() {
  assertThat(SmtpNotificationTransport.safeHttps("https://stoxsim.test")).isTrue();
  for(String url:new String[]{"http://stoxsim.test","https://user:pass@stoxsim.test","https://stoxsim.test?x=y","https://stoxsim.test#fragment","invalid"})assertThat(SmtpNotificationTransport.safeHttps(url)).isFalse();
 }
}
