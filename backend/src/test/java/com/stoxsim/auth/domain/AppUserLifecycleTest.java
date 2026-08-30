package com.stoxsim.auth.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppUserLifecycleTest {

    @Test
    void newLearnersHaveNoPlatformAdministrationAuthority() {
        var user = new AppUser(
            "learner@example.com",
            "password-hash",
            "Market Learner"
        );

        assertEquals(PlatformRole.USER, user.getPlatformRole());
        assertFalse(user.isPlatformAdmin());
    }

    @Test
    void changingEmailRequiresFreshVerification() {
        var user = new AppUser(
            "learner@example.com",
            "password-hash",
            "Market Learner"
        );

        assertFalse(user.isEmailVerified());
        user.markEmailVerified();
        assertTrue(user.isEmailVerified());

        boolean changed = user.updateProfile("new@example.com", "Market Learner");

        assertTrue(changed);
        assertFalse(user.isEmailVerified());
    }

    @Test
    void recordsVersionedLegalAcceptance() {
        var user = new AppUser(
            "learner@example.com",
            "password-hash",
            "Market Learner"
        );

        user.acceptLegalDocuments("2026-08-19", "2026-08-19");

        assertNotNull(user.getTermsAcceptedAt());
        assertEquals("2026-08-19", user.getTermsVersion());
        assertEquals("2026-08-19", user.getPrivacyVersion());
    }

    @Test
    void changingOnlyDisplayNameKeepsVerification() {
        var user = new AppUser(
            "learner@example.com",
            "password-hash",
            "Market Learner"
        );
        user.markEmailVerified();

        boolean changed = user.updateProfile("learner@example.com", "Updated Learner");

        assertFalse(changed);
        assertTrue(user.isEmailVerified());
    }

    @Test
    void onboardingProgressIsIdempotentAndBackendOwned() {
        var user = new AppUser(
            "learner@example.com",
            "password-hash",
            "Market Learner"
        );

        user.completeOnboardingIntroduction();
        var introductionTime = user.getOnboardingIntroCompletedAt();
        user.completeOnboardingIntroduction();
        user.recordFirstOrderPlaced();
        var firstOrderTime = user.getFirstOrderPlacedAt();
        user.recordFirstOrderPlaced();

        assertNotNull(introductionTime);
        assertEquals(introductionTime, user.getOnboardingIntroCompletedAt());
        assertNotNull(firstOrderTime);
        assertEquals(firstOrderTime, user.getFirstOrderPlacedAt());
    }

    @Test
    void learnerCanPersistentlyDismissTheGuide() {
        var user = new AppUser(
            "learner@example.com",
            "password-hash",
            "Market Learner"
        );

        user.dismissOnboarding();

        assertNotNull(user.getOnboardingDismissedAt());
    }
}
