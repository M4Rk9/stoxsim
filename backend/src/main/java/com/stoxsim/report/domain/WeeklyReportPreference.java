package com.stoxsim.report.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "weekly_report_preference")
public class WeeklyReportPreference {

    public static final String DEFAULT_ZONE_ID = "Asia/Kolkata";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WeeklyReportPreference() {
    }

    public WeeklyReportPreference(AppUser user) {
        this.user = user;
        this.userId = user.getId();
        this.enabled = false;
        this.zoneId = DEFAULT_ZONE_ID;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(boolean enabled, String zoneId) {
        this.enabled = enabled;
        this.zoneId = zoneId;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getZoneId() {
        return zoneId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
