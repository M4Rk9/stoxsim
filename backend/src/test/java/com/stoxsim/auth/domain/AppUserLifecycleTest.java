package com.stoxsim.auth.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppUserLifecycleTest {

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
}
