package com.stoxsim.campus.domain;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "campus_verification_request")
public class CampusVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private AppUser requester;

    @Column(name = "institution_name", nullable = false, length = 160)
    private String institutionName;

    @Column(name = "normalized_name", nullable = false, length = 160)
    private String normalizedName;

    @Column(name = "email_domain", nullable = false, length = 190)
    private String emailDomain;

    @Column(name = "website_url", length = 300)
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 16)
    private CampusRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private AppUser reviewedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected CampusVerificationRequest() {
    }

    public CampusVerificationRequest(
        AppUser requester,
        String institutionName,
        String normalizedName,
        String emailDomain,
        String websiteUrl,
        Instant submittedAt
    ) {
        this.requester = requester;
        this.institutionName = institutionName;
        this.normalizedName = normalizedName;
        this.emailDomain = emailDomain;
        this.websiteUrl = websiteUrl;
        this.status = CampusRequestStatus.PENDING;
        this.submittedAt = submittedAt;
    }

    public void approve(AppUser reviewer, String note, Instant now) {
        requirePending();
        status = CampusRequestStatus.APPROVED;
        reviewedBy = reviewer;
        reviewNote = note;
        reviewedAt = now;
    }

    public void reject(AppUser reviewer, String note, Instant now) {
        requirePending();
        status = CampusRequestStatus.REJECTED;
        reviewedBy = reviewer;
        reviewNote = note;
        reviewedAt = now;
    }

    private void requirePending() {
        if (status != CampusRequestStatus.PENDING) {
            throw new IllegalStateException("Campus verification request is already reviewed");
        }
    }

    public UUID getId() {
        return id;
    }

    public AppUser getRequester() {
        return requester;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getEmailDomain() {
        return emailDomain;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public CampusRequestStatus getStatus() {
        return status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
