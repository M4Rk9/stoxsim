package com.stoxsim.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 16)
    private PlatformRole platformRole;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "terms_version", length = 32)
    private String termsVersion;

    @Column(name = "privacy_version", length = 32)
    private String privacyVersion;

    @Column(name = "onboarding_intro_completed_at")
    private Instant onboardingIntroCompletedAt;

    @Column(name = "first_order_placed_at")
    private Instant firstOrderPlacedAt;

    @Column(name = "onboarding_dismissed_at")
    private Instant onboardingDismissedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.platformRole = PlatformRole.USER;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PlatformRole getPlatformRole() {
        return platformRole;
    }

    public boolean isPlatformAdmin() {
        return platformRole == PlatformRole.ADMIN;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public String getPrivacyVersion() {
        return privacyVersion;
    }

    public Instant getOnboardingIntroCompletedAt() {
        return onboardingIntroCompletedAt;
    }

    public Instant getFirstOrderPlacedAt() {
        return firstOrderPlacedAt;
    }

    public Instant getOnboardingDismissedAt() {
        return onboardingDismissedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean updateProfile(String email, String displayName) {
        boolean emailChanged = !this.email.equalsIgnoreCase(email);
        this.email = email;
        this.displayName = displayName;
        if (emailChanged) {
            this.emailVerifiedAt = null;
        }
        this.updatedAt = Instant.now();
        return emailChanged;
    }

    public void markEmailVerified() {
        this.emailVerifiedAt = Instant.now();
        this.updatedAt = this.emailVerifiedAt;
    }

    public void acceptLegalDocuments(String termsVersion, String privacyVersion) {
        this.termsAcceptedAt = Instant.now();
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.updatedAt = this.termsAcceptedAt;
    }

    public void completeOnboardingIntroduction() {
        if (onboardingIntroCompletedAt == null) {
            onboardingIntroCompletedAt = Instant.now();
            onboardingDismissedAt = null;
            updatedAt = onboardingIntroCompletedAt;
        }
    }

    public void recordFirstOrderPlaced() {
        if (firstOrderPlacedAt == null) {
            firstOrderPlacedAt = Instant.now();
            updatedAt = firstOrderPlacedAt;
        }
    }

    public void dismissOnboarding() {
        if (onboardingDismissedAt == null) {
            onboardingDismissedAt = Instant.now();
            updatedAt = onboardingDismissedAt;
        }
    }

    public void resumeOnboarding() {
        if (onboardingDismissedAt != null) {
            onboardingDismissedAt = null;
            updatedAt = Instant.now();
        }
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }
}
