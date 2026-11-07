package com.stoxsim.billing;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    public record Checkout(@NotNull @Pattern(regexp="PLUS|PRO") String plan,@NotNull UUID requestKey) {}
    public record Reconcile(@NotNull @Pattern(regexp="sub_[A-Za-z0-9]+") String providerReference) {}
    public record PlayPurchase(@NotBlank @Size(max=4096) String purchaseToken,@NotBlank @Size(max=150) String productId) {}
    private final LiveBillingService billing;private final PlayNotificationVerifier verifier;
    public BillingController(LiveBillingService billing,PlayNotificationVerifier verifier){this.billing=billing;this.verifier=verifier;}
    private UUID actor(Jwt jwt){return UUID.fromString(jwt.getSubject());}private <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    @GetMapping public ResponseEntity<?> overview(@AuthenticationPrincipal Jwt jwt){return ok(billing.overview(actor(jwt)));}
    @GetMapping("/play/config") public ResponseEntity<?> playConfig(@AuthenticationPrincipal Jwt jwt){return ok(billing.playConfig(actor(jwt)));}
    @PostMapping("/subscriptions") public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Checkout body){return ok(billing.create(actor(jwt),body.plan(),body.requestKey()));}
    @PostMapping("/subscriptions/{id}/refresh") public ResponseEntity<?> refresh(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ok(billing.refresh(actor(jwt),id));}
    @PostMapping("/subscriptions/{id}/cancel") public ResponseEntity<?> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return ok(billing.cancel(actor(jwt),id));}
    @PostMapping("/subscriptions/{id}/reconcile") public ResponseEntity<?> reconcile(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Reconcile body){return ok(billing.reconcile(actor(jwt),id,body.providerReference()));}
    @PostMapping("/play/verify") public ResponseEntity<?> verify(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PlayPurchase body){return ok(billing.verifyPlay(actor(jwt),body.purchaseToken(),body.productId()));}
    @GetMapping("/admin/revenue") public ResponseEntity<?> revenue(@AuthenticationPrincipal Jwt jwt){return ok(billing.revenue(actor(jwt)));}
    @PostMapping("/razorpay/webhook") public ResponseEntity<Void> razorpay(HttpServletRequest request)throws IOException{billing.razorpayWebhook(bytes(request),request.getHeader("X-Razorpay-Signature"),request.getHeader("X-Razorpay-Event-Id"));return ok(null);}
    @PostMapping("/play/notifications") public ResponseEntity<Void> play(HttpServletRequest request)throws IOException{verifier.verify(request.getHeader("Authorization"));billing.playNotification(bytes(request));return ok(null);}
    private byte[] bytes(HttpServletRequest request)throws IOException{byte[] body=request.getInputStream().readNBytes(65537);if(body.length>65536)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Provider notification too large");return body;}
}
