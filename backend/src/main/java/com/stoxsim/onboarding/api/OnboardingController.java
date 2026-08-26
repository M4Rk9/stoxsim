package com.stoxsim.onboarding.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.onboarding.service.OnboardingService;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @GetMapping
    public OnboardingStateResponse state(@AuthenticationPrincipal Jwt jwt) {
        return onboarding.state(userId(jwt));
    }

    @PostMapping("/introduction/complete")
    public OnboardingStateResponse completeIntroduction(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return onboarding.completeIntroduction(userId(jwt));
    }

    @PostMapping("/dismiss")
    public OnboardingStateResponse dismiss(@AuthenticationPrincipal Jwt jwt) {
        return onboarding.dismiss(userId(jwt));
    }

    @PostMapping("/resume")
    public OnboardingStateResponse resume(@AuthenticationPrincipal Jwt jwt) {
        return onboarding.resume(userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
