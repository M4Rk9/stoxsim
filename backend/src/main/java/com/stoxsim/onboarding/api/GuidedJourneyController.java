package com.stoxsim.onboarding.api;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.stoxsim.onboarding.service.GuidedJourneyService;

@RestController
public class GuidedJourneyController {
    private final GuidedJourneyService journey;
    public GuidedJourneyController(GuidedJourneyService journey) { this.journey = journey; }
    @GetMapping("/api/v1/onboarding/journey")
    public ResponseEntity<GuidedJourneyService.State> state(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(journey.state(UUID.fromString(jwt.getSubject())));
    }
}
