package com.stoxsim.notification;
import static com.stoxsim.notification.NotificationDtos.*;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/notifications")
public class NotificationController {
 private final NotificationService service;
 public NotificationController(NotificationService service) { this.service=service; }
 @GetMapping public Inbox inbox(@AuthenticationPrincipal Jwt jwt) {return service.inbox(user(jwt));}
 @GetMapping("/preferences") public Preferences preferences(@AuthenticationPrincipal Jwt jwt) {return service.preferences(user(jwt));}
 @PutMapping("/preferences") public Preferences preferences(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Preferences p) {return service.updatePreferences(user(jwt),p);}
 @PostMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {service.markRead(user(jwt),id);}
 @PostMapping("/read-all") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void readAll(@AuthenticationPrincipal Jwt jwt) {service.markAllRead(user(jwt));}
 @PostMapping("/devices") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void device(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Device d) {service.registerDevice(user(jwt),d);}
 @DeleteMapping("/devices") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void removeDevice(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody RemoveDevice d) {service.removeDevice(user(jwt),d.token());}
 @PostMapping("/unsubscribe") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void unsubscribe(@RequestParam(required=false) String token) {service.unsubscribe(token);}
 @PostMapping("/admin/suppressions") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void suppress(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Suppression s) {service.administratorSuppress(user(jwt),s.userId(),s.reason());}
 private UUID user(Jwt jwt) {return UUID.fromString(jwt.getSubject());}
}
