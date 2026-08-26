package com.stoxsim.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private AppUserRepository users;

    private OnboardingService onboarding;
    private UUID userId;
    private AppUser user;

    @BeforeEach
    void setUp() {
        onboarding = new OnboardingService(users);
        userId = UUID.randomUUID();
        user = new AppUser("learner@example.com", "hash", "Learner");
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void advancesFromIntroductionToBackendRecordedFirstOrder() {
        var introduced = onboarding.completeIntroduction(userId);

        assertThat(introduced.introductionCompleted()).isTrue();
        assertThat(introduced.firstOrderCompleted()).isFalse();
        assertThat(introduced.nextStep()).isEqualTo("FIRST_TRADE");

        onboarding.recordFirstOrder(userId);
        var completed = onboarding.state(userId);

        assertThat(completed.completed()).isTrue();
        assertThat(completed.nextStep()).isEqualTo("COMPLETE");
    }

    @Test
    void dismissesWithoutPretendingTheLearningPathWasCompleted() {
        var dismissed = onboarding.dismiss(userId);

        assertThat(dismissed.dismissed()).isTrue();
        assertThat(dismissed.completed()).isFalse();
        assertThat(dismissed.nextStep()).isEqualTo("DISMISSED");

        var resumed = onboarding.resume(userId);

        assertThat(resumed.dismissed()).isFalse();
        assertThat(resumed.nextStep()).isEqualTo("INTRODUCTION");
    }
}
