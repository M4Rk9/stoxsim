package com.stoxsim.campus.domain;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "campus_institution")
public class CampusInstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 160)
    private String normalizedName;

    @Column(name = "email_domain", nullable = false, length = 190)
    private String emailDomain;

    @Column(name = "website_url", length = 300)
    private String websiteUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private AppUser verifiedBy;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    protected CampusInstitution() {
    }

    public CampusInstitution(
        String name,
        String normalizedName,
        String emailDomain,
        String websiteUrl,
        AppUser verifiedBy,
        Instant verifiedAt
    ) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.emailDomain = emailDomain;
        this.websiteUrl = websiteUrl;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = verifiedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
