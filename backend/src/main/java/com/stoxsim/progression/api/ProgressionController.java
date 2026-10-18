package com.stoxsim.progression.api;

import java.util.UUID;
import com.stoxsim.onboarding.service.GuidedJourneyService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.progression.service.ProgressionService;

@RestController
@RequestMapping("/api/v1/progression")
public class ProgressionController {

    private final ProgressionService progression;

    private final GuidedJourneyService journey;

    public ProgressionController(ProgressionService progression, GuidedJourneyService journey) {
        this.journey = journey;
        this.progression = progression;
    }

    @GetMapping
    public ProgressionResponse state(@AuthenticationPrincipal Jwt jwt) {
        return progression.state(userId(jwt));
    }

    @PostMapping("/visit")
    public ProgressionResponse visit(@AuthenticationPrincipal Jwt jwt) {
        var result = progression.state(userId(jwt));
        journey.record(userId(jwt), GuidedJourneyService.Event.LEARNING);
        return result;
    }

    @PostMapping("/check-in")
    public ProgressionResponse checkIn(@AuthenticationPrincipal Jwt jwt) {
        return progression.checkIn(userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
