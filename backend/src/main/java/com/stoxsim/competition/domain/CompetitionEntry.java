package com.stoxsim.competition.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;

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
@Table(name = "competition_entry")
public class CompetitionEntry {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private CompetitionSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "baseline_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal baselineValue;

    @Column(name = "latest_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal latestValue;

    @Column(name = "return_percent", nullable = false, precision = 12, scale = 4)
    private BigDecimal returnPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_status", nullable = false, length = 24)
    private PricingStatus dataStatus;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "valued_at", nullable = false)
    private Instant valuedAt;

    protected CompetitionEntry() {
    }

    public CompetitionEntry(
        CompetitionSeason season,
        AppUser user,
        BigDecimal baselineValue,
        PricingStatus dataStatus,
        Instant now
    ) {
        this.season = season;
        this.user = user;
        this.baselineValue = money(baselineValue);
        this.latestValue = this.baselineValue;
        this.returnPercent = money(BigDecimal.ZERO);
        this.dataStatus = dataStatus;
        this.joinedAt = now;
        this.valuedAt = now;
    }

    public void refresh(BigDecimal value, PricingStatus status, Instant valuedAt) {
        latestValue = money(value);
        returnPercent = value.subtract(baselineValue)
            .multiply(HUNDRED)
            .divide(baselineValue, 4, RoundingMode.HALF_UP);
        dataStatus = status;
        this.valuedAt = valuedAt;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public CompetitionSeason getSeason() { return season; }
    public AppUser getUser() { return user; }
    public BigDecimal getBaselineValue() { return baselineValue; }
    public BigDecimal getLatestValue() { return latestValue; }
    public BigDecimal getReturnPercent() { return returnPercent; }
    public PricingStatus getDataStatus() { return dataStatus; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getValuedAt() { return valuedAt; }
}
