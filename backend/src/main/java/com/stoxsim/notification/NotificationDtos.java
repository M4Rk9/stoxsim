package com.stoxsim.notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class NotificationDtos {
 private NotificationDtos() {}
 public record Preferences(@NotNull Boolean learning,@NotNull Boolean competitions,@NotNull Boolean portfolioEducation,
     @NotNull Boolean productAnnouncements,@NotNull Boolean promotionalMessages,@NotNull Boolean pushEnabled,@NotNull Boolean emailDigest) {
  public static Preferences defaults() { return new Preferences(true,true,true,false,false,false,false); }
  public boolean allows(NotificationCategory category) {
   return switch(category) { case SECURITY,BILLING->true; case LEARNING->learning; case COMPETITION->competitions;
    case PORTFOLIO_EDUCATION->portfolioEducation; case PRODUCT->productAnnouncements; };
  }
  public boolean allows(NotificationCategory category,boolean promotional) {
   return allows(category) && (!promotional || (category==NotificationCategory.PRODUCT && promotionalMessages));
  }
 }
 public record Item(UUID id,NotificationCategory category,String title,String body,String href,Instant createdAt,Instant readAt) {}
 public record Inbox(List<Item> items,long unreadCount) {}
 public record Device(@NotBlank @Size(max=4096) String token,@NotBlank @Pattern(regexp="ANDROID") String platform) {}
 public record RemoveDevice(@NotBlank @Size(max=4096) String token) {}
 public record Suppression(@NotNull UUID userId,@NotBlank @Pattern(regexp="BOUNCE|COMPLAINT") String reason) {}
}
