package com.stoxsim.finwiz.api;

import java.util.UUID;
import com.stoxsim.onboarding.service.GuidedJourneyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.finwiz.service.FinwizService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/finwiz")
public class FinwizController {

    private final FinwizService finwiz;

    private final GuidedJourneyService journey;

    public FinwizController(FinwizService finwiz, GuidedJourneyService journey) {
        this.journey = journey;
        this.finwiz = finwiz;
    }

    @PostMapping("/ask")
    public FinwizResponse ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FinwizRequest request) {
        var result = finwiz.ask(UUID.fromString(jwt.getSubject()), request);
        journey.record(UUID.fromString(jwt.getSubject()), GuidedJourneyService.Event.FINWIZ);
        return result;
    }
}
