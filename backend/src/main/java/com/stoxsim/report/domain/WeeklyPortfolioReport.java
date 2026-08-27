package com.stoxsim.report.domain;

import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "weekly_portfolio_report")
public class WeeklyPortfolioReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 24)
    private WeeklyReportDeliveryStatus deliveryStatus;

    @Column(name = "delivery_attempted_at")
    private Instant deliveryAttemptedAt;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    protected WeeklyPortfolioReport() {
    }

    public WeeklyPortfolioReport(
        AppUser user,
        LocalDate periodStart,
        LocalDate periodEnd,
        String snapshotJson,
        Instant generatedAt
    ) {
        this.user = user;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.snapshotJson = snapshotJson;
        this.deliveryStatus = WeeklyReportDeliveryStatus.PENDING;
        this.deliveryAttempts = 0;
        this.generatedAt = generatedAt;
    }

    public void recordDelivery(boolean delivered, Instant attemptedAt) {
        deliveryStatus = delivered
            ? WeeklyReportDeliveryStatus.SENT
            : WeeklyReportDeliveryStatus.FAILED;
        deliveryAttemptedAt = attemptedAt;
        deliveryAttempts++;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public WeeklyReportDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public Instant getDeliveryAttemptedAt() {
        return deliveryAttemptedAt;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
