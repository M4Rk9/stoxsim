package com.stoxsim.rewards;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rewards")
public class ReferralController {
    private final ReferralService referrals;
    public ReferralController(ReferralService referrals) { this.referrals = referrals; }

    @GetMapping
    public ResponseEntity<ReferralService.Overview> overview(@AuthenticationPrincipal Jwt jwt) {
        return privateResponse(referrals.overview(actor(jwt)));
    }

    @PostMapping("/code")
    public ResponseEntity<ReferralService.Overview> create(@AuthenticationPrincipal Jwt jwt) {
        return privateResponse(referrals.createCode(actor(jwt)));
    }

    @GetMapping("/review")
    public ResponseEntity<List<ReferralService.Review>> queue(@AuthenticationPrincipal Jwt jwt) {
        return privateResponse(referrals.reviewQueue(actor(jwt)));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<ReferralService.Reconciliation> reconcile(@AuthenticationPrincipal Jwt jwt) {
        return privateResponse(referrals.reconcile(actor(jwt)));
    }

    @PostMapping("/review/{id}/approve")
    public ResponseEntity<ReferralService.Outcome> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return privateResponse(referrals.decide(actor(jwt),id,true));
    }

    @PostMapping("/review/{id}/reject")
    public ResponseEntity<ReferralService.Outcome> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return privateResponse(referrals.decide(actor(jwt),id,false));
    }

    @PostMapping("/review/{id}/reverse")
    public ResponseEntity<ReferralService.Outcome> reverse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return privateResponse(referrals.reverse(actor(jwt),id));
    }

    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
