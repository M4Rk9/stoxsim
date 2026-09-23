package com.stoxsim.subscription.api;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.subscription.service.RazorpayTestBillingService;

@RestController
@RequestMapping("/api/v1/billing/test")
public class RazorpayTestController {
    public record Checkout(@NotNull @Pattern(regexp="PLUS|PRO") String plan,@NotNull UUID requestKey) {}
    public record Reconcile(@NotNull @Pattern(regexp="sub_[A-Za-z0-9]+") String providerId) {}
    public record Benefits(@NotNull Boolean enabled) {}
    private final RazorpayTestBillingService service;
    public RazorpayTestController(RazorpayTestBillingService service) { this.service=service; }
    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private <T> ResponseEntity<T> ok(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
    @GetMapping public ResponseEntity<?> overview(@AuthenticationPrincipal Jwt jwt) { return ok(service.overview(actor(jwt))); }
    @PostMapping("/subscriptions") public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Checkout body) {
        return ok(service.create(actor(jwt),body.plan(),body.requestKey()));
    }
    @PostMapping("/subscriptions/{id}/refresh") public ResponseEntity<?> refresh(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {
        return ok(service.refresh(actor(jwt),id));
    }
    @PostMapping("/subscriptions/{id}/cancel") public ResponseEntity<?> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {
        return ok(service.cancel(actor(jwt),id));
    }
    @PostMapping("/subscriptions/{id}/reconcile") public ResponseEntity<?> reconcile(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Reconcile body) {
        return ok(service.reconcile(actor(jwt),id,body.providerId()));
    }
    @PostMapping("/webhook") public ResponseEntity<Void> webhook(HttpServletRequest request) throws IOException {
        // Bound raw bytes even for chunked transfer, before parsing or verification.
        byte[] raw=request.getInputStream().readNBytes(65537);
        if(raw.length>65536) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Webhook too large");
        service.webhook(raw,request.getHeader("X-Razorpay-Signature"),request.getHeader("X-Razorpay-Event-Id"));
        return ok(null);
    }
    @PostMapping("/subscriptions/{id}/benefits") public ResponseEntity<?> benefits(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Benefits body) {
        return ok(service.benefits(actor(jwt),id,body.enabled()));
    }
}
