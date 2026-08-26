package com.stoxsim.onboarding.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.common.error.UnauthorizedException;
import com.stoxsim.onboarding.api.OnboardingStateResponse;

@Service
public class OnboardingService {

    private final AppUserRepository users;

    public OnboardingService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public OnboardingStateResponse state(UUID userId) {
        return OnboardingStateResponse.from(requireUser(userId));
    }

    @Transactional
    public OnboardingStateResponse completeIntroduction(UUID userId) {
        AppUser user = requireUser(userId);
        user.completeOnboardingIntroduction();
        return OnboardingStateResponse.from(user);
    }

    @Transactional
    public OnboardingStateResponse dismiss(UUID userId) {
        AppUser user = requireUser(userId);
        user.dismissOnboarding();
        return OnboardingStateResponse.from(user);
    }

    @Transactional
    public OnboardingStateResponse resume(UUID userId) {
        AppUser user = requireUser(userId);
        user.resumeOnboarding();
        return OnboardingStateResponse.from(user);
    }

    @Transactional
    public void recordFirstOrder(UUID userId) {
        requireUser(userId).recordFirstOrderPlaced();
    }

    private AppUser requireUser(UUID userId) {
        return users.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }
}
